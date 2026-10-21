package bdv.movie;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Keyframe editor: add/remove keyframes, preview, export/import JSON,
 * and render a PNG sequence.
 */
public class ProduceMovieDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	private final ViewerPanel viewer;
	private final PreviewPlayer preview;
	private final List< MovieFramePanel > framePanels = new ArrayList<>();
	private final JPanel framesStrip = new JPanel( new FlowLayout( FlowLayout.LEFT ) );

	private final JSpinner strideSpinner = new JSpinner( new SpinnerNumberModel( 10, 1, 1000, 1 ) );
	private final JSpinner sleepSpinner = new JSpinner( new SpinnerNumberModel( 50, 1, 5000, 10 ) );
	private final JCheckBox interpolateTime = new JCheckBox( "Capture timepoint with keyframe", true );

	private final JButton removeButton = new JButton( "\u2212" );
	private final JButton exportJsonButton = new JButton( "Export JSON\u2026" );
	private final JButton recordButton = new JButton( "Record PNGs\u2026" );

	private final JProgressBar progressBar = new JProgressBar( 0, 1000 );
	private final JButton cancelButton = new JButton( "Cancel" );
	private final AtomicBoolean cancelRecording = new AtomicBoolean();

	private File lastDir = new File( System.getProperty( "user.home" ) );

	public ProduceMovieDialog( final Frame owner, final ViewerPanel viewer )
	{
		super( owner, "Produce Movie", false );
		this.viewer = viewer;
		this.preview = new PreviewPlayer( viewer );

		// --- preview row
		final JPanel previewRow = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
		previewRow.add( new JLabel( "Preview: every" ) );
		previewRow.add( strideSpinner );
		previewRow.add( new JLabel( "th frame,  delay" ) );
		previewRow.add( sleepSpinner );
		previewRow.add( new JLabel( "ms" ) );
		final JButton play = new JButton( "\u25B6 Play" );
		play.addActionListener( e -> startPreview() );
		final JButton stop = new JButton( "\u25A0 Stop" );
		stop.addActionListener( e -> preview.stop() );
		previewRow.add( play );
		previewRow.add( stop );
		previewRow.add( interpolateTime );

		// --- keyframe strip
		final JPanel addRemove = new JPanel( new GridLayout( 2, 1, 0, 4 ) );
		final JButton addButton = new JButton( "+" );
		addButton.setToolTipText( "Add the current view as a keyframe" );
		addButton.addActionListener( e -> addCurrentView() );
		removeButton.setToolTipText( "Remove the last keyframe" );
		removeButton.addActionListener( e -> removeLast() );
		addRemove.add( addButton );
		addRemove.add( removeButton );
		final JPanel addRemoveWrap = new JPanel();
		addRemoveWrap.add( addRemove );

		final JScrollPane scroll = new JScrollPane( framesStrip,
				JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
		scroll.setBorder( BorderFactory.createTitledBorder( "Keyframes" ) );
		scroll.setPreferredSize( new Dimension( 720, 230 ) );

		final JPanel io = new JPanel();
		io.setLayout( new BoxLayout( io, BoxLayout.PAGE_AXIS ) );
		exportJsonButton.addActionListener( e -> exportJson() );
		final JButton importButton = new JButton( "Import JSON\u2026" );
		importButton.addActionListener( e -> importJson() );
		recordButton.addActionListener( e -> showRecordDialog() );
		for ( final JButton b : new JButton[] { exportJsonButton, importButton, recordButton } )
		{
			b.setAlignmentX( 0.5f );
			b.setMaximumSize( new Dimension( Integer.MAX_VALUE, b.getPreferredSize().height ) );
			io.add( b );
		}

		final JPanel center = new JPanel( new BorderLayout() );
		center.add( addRemoveWrap, BorderLayout.WEST );
		center.add( scroll, BorderLayout.CENTER );
		center.add( io, BorderLayout.EAST );

		// --- progress row
		final JPanel progressRow = new JPanel( new BorderLayout( 6, 0 ) );
		progressBar.setStringPainted( true );
		progressBar.setString( "" );
		cancelButton.setEnabled( false );
		cancelButton.addActionListener( e -> cancelRecording.set( true ) );
		progressRow.add( progressBar, BorderLayout.CENTER );
		progressRow.add( cancelButton, BorderLayout.EAST );
		progressRow.setBorder( BorderFactory.createEmptyBorder( 4, 6, 6, 6 ) );

		getContentPane().setLayout( new BorderLayout() );
		getContentPane().add( previewRow, BorderLayout.NORTH );
		getContentPane().add( center, BorderLayout.CENTER );
		getContentPane().add( progressRow, BorderLayout.SOUTH );

		// ESC hides
		getRootPane().getInputMap( JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT )
				.put( KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ), "hide" );
		getRootPane().getActionMap().put( "hide", new AbstractAction()
		{
			private static final long serialVersionUID = 1L;
			@Override public void actionPerformed( final ActionEvent e ) { setVisible( false ); }
		} );
		addWindowListener( new WindowAdapter()
		{
			@Override public void windowClosing( final WindowEvent e ) { preview.stop(); }
		} );
		setDefaultCloseOperation( HIDE_ON_CLOSE );

		updateButtons();
		pack();
		setLocationRelativeTo( owner );
	}

	// ------------------------------------------------------------ keyframes

	private List< MovieFrame > keyframes()
	{
		final List< MovieFrame > list = new ArrayList<>();
		for ( final MovieFramePanel p : framePanels )
			list.add( p.getMovieFrame() );
		return list;
	}

	private void addCurrentView()
	{
		final AffineTransform3D t = viewer.state().getViewerTransform();
		final Integer tp = interpolateTime.isSelected() ? viewer.state().getCurrentTimepoint() : null;
		final MovieFrame f = new MovieFrame( framePanels.size(), t,
				viewer.getDisplay().getWidth(), viewer.getDisplay().getHeight(), tp );
		addPanel( f, MovieFramePanel.snapshot( viewer ) );
	}

	private void addPanel( final MovieFrame f, final BufferedImage thumb )
	{
		final MovieFramePanel p = new MovieFramePanel( f, thumb );
		framePanels.add( p );
		framesStrip.add( p );
		framesStrip.revalidate();
		framesStrip.repaint();
		updateButtons();
	}

	private void removeLast()
	{
		if ( framePanels.isEmpty() )
			return;
		framesStrip.remove( framePanels.remove( framePanels.size() - 1 ) );
		framesStrip.revalidate();
		framesStrip.repaint();
		updateButtons();
	}

	private void updateButtons()
	{
		final boolean two = framePanels.size() >= 2;
		exportJsonButton.setEnabled( two );
		recordButton.setEnabled( two );
		removeButton.setEnabled( !framePanels.isEmpty() );
	}

	// ------------------------------------------------------------ preview

	private void startPreview()
	{
		if ( framePanels.size() < 2 )
			return;
		preview.play( keyframes(), ( Integer ) strideSpinner.getValue(), ( Integer ) sleepSpinner.getValue() );
	}

	// ------------------------------------------------------------ JSON

	private void exportJson()
	{
		final JFileChooser fc = new JFileChooser( lastDir );
		fc.setFileFilter( new FileNameExtensionFilter( "JSON file", "json" ) );
		if ( fc.showSaveDialog( this ) != JFileChooser.APPROVE_OPTION )
			return;
		File file = fc.getSelectedFile();
		if ( !file.getName().toLowerCase().endsWith( ".json" ) )
			file = new File( file.getParentFile(), file.getName() + ".json" );
		lastDir = file.getParentFile();
		try
		{
			MovieFramesSerializer.save( keyframes(), file );
		}
		catch ( final Exception e )
		{
			error( "Could not save " + file + ":\n" + e );
		}
	}

	private void importJson()
	{
		final JFileChooser fc = new JFileChooser( lastDir );
		fc.setFileFilter( new FileNameExtensionFilter( "JSON file", "json" ) );
		if ( fc.showOpenDialog( this ) != JFileChooser.APPROVE_OPTION )
			return;
		final File file = fc.getSelectedFile();
		lastDir = file.getParentFile();
		final List< MovieFrame > loaded;
		try
		{
			loaded = MovieFramesSerializer.load( file );
		}
		catch ( final Exception e )
		{
			error( "Could not read " + file + ":\n" + e );
			return;
		}
		preview.stop();
		while ( !framePanels.isEmpty() )
			removeLast();

		// Visit each keyframe in the live viewer to grab a thumbnail. Runs on
		// a worker thread so the viewer can actually render in between.
		new Thread( () -> {
			final int nTp = viewer.state().getNumTimepoints();
			try
			{
				for ( final MovieFrame f : loaded )
				{
					SwingUtilities.invokeAndWait( () -> {
						viewer.state().setViewerTransform( f.getTransform() );
						if ( f.getTimepoint() != null && f.getTimepoint() >= 0 && f.getTimepoint() < nTp )
							viewer.state().setCurrentTimepoint( f.getTimepoint() );
					} );
					Thread.sleep( 300 );
					SwingUtilities.invokeAndWait( () -> addPanel( f, MovieFramePanel.snapshot( viewer ) ) );
				}
			}
			catch ( final Exception e )
			{
				SwingUtilities.invokeLater( () -> error( "Import interrupted: " + e ) );
			}
		}, "BDV movie import" ).start();
	}

	// ------------------------------------------------------------ recording

	private void showRecordDialog()
	{
		preview.stop();
		final JTextField dirField = new JTextField( new File( lastDir, "bdv-movie" ).getAbsolutePath(), 28 );
		final JButton browse = new JButton( "Browse\u2026" );
		browse.addActionListener( e -> {
			final JFileChooser fc = new JFileChooser( dirField.getText() );
			fc.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
			if ( fc.showSaveDialog( this ) == JFileChooser.APPROVE_OPTION )
				dirField.setText( fc.getSelectedFile().getAbsolutePath() );
		} );
		final JSpinner w = new JSpinner( new SpinnerNumberModel( Math.max( 10, viewer.getDisplay().getWidth() ), 10, 10000, 1 ) );
		final JSpinner h = new JSpinner( new SpinnerNumberModel( Math.max( 10, viewer.getDisplay().getHeight() ), 10, 10000, 1 ) );
		final JCheckBox scaleBar = new JCheckBox( "Scale bar", bdv.util.Prefs.showScaleBarInMovie() );
		final JCheckBox box = new JCheckBox( "Box overlay", false );

		final JPanel dirRow = new JPanel( new BorderLayout( 4, 0 ) );
		dirRow.add( new JLabel( "Output folder " ), BorderLayout.WEST );
		dirRow.add( dirField, BorderLayout.CENTER );
		dirRow.add( browse, BorderLayout.EAST );
		final JPanel sizeRow = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
		sizeRow.add( new JLabel( "Width" ) );
		sizeRow.add( w );
		sizeRow.add( new JLabel( "Height" ) );
		sizeRow.add( h );
		sizeRow.add( scaleBar );
		sizeRow.add( box );
		final JPanel panel = new JPanel( new GridLayout( 2, 1 ) );
		panel.add( dirRow );
		panel.add( sizeRow );

		if ( JOptionPane.showConfirmDialog( this, panel, "Record PNG sequence",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE ) != JOptionPane.OK_OPTION )
			return;

		final File dir = new File( dirField.getText() );
		dir.mkdirs();
		if ( !dir.isDirectory() )
		{
			error( "Invalid output folder: " + dir );
			return;
		}
		lastDir = dir.getParentFile() != null ? dir.getParentFile() : dir;
		record( keyframes(), ( Integer ) w.getValue(), ( Integer ) h.getValue(), scaleBar.isSelected(), box.isSelected(), dir );
	}

	private void record( final List< MovieFrame > frames, final int width, final int height,
			final boolean scaleBar, final boolean box, final File dir )
	{
		cancelRecording.set( false );
		recordButton.setEnabled( false );
		cancelButton.setEnabled( true );
		progressBar.setValue( 0 );
		progressBar.setString( "Recording\u2026" );

		new Thread( () -> {
			String msg;
			final long t0 = System.nanoTime();
			try
			{
				final int n = MovieRecorder.record( viewer, frames, width, height, scaleBar, box, dir,
						p -> SwingUtilities.invokeLater( () -> {
							progressBar.setValue( ( int ) ( 1000 * p ) );
							progressBar.setString( String.format( "Recording\u2026 %.0f%%", 100 * p ) );
						} ),
						cancelRecording::get );
				msg = String.format( "%s%d frames to %s (%.1f s)", cancelRecording.get() ? "Cancelled after " : "Wrote ",
						n, dir, ( System.nanoTime() - t0 ) * 1e-9 );
			}
			catch ( final Throwable e )
			{
				e.printStackTrace();
				msg = "Recording failed: " + e;
			}
			final String m = msg;
			SwingUtilities.invokeLater( () -> {
				progressBar.setString( m );
				cancelButton.setEnabled( false );
				updateButtons();
			} );
		}, "BDV movie recorder" ).start();
	}

	private void error( final String msg )
	{
		JOptionPane.showMessageDialog( this, msg, "Movie Producer", JOptionPane.ERROR_MESSAGE );
	}
}
