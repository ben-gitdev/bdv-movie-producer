package bdv.movie;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

/** Thumbnail plus "frames" and easing fields for one keyframe. */
public class MovieFramePanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	private final MovieFrame movieFrame;
	private final TitledBorder title;
	private final Thumbnail thumbnail;
	private final JTextField framesField;
	private final JComboBox< String > accelField;

	public MovieFramePanel( final MovieFrame movieFrame, final BufferedImage thumbnail )
	{
		this.movieFrame = movieFrame;
		title = BorderFactory.createTitledBorder( BorderFactory.createLineBorder( Color.lightGray ), "" );
		title.setTitleJustification( TitledBorder.CENTER );
		setBorder( title );
		setPreferredSize( new Dimension( 140, 180 ) );

		this.thumbnail = new Thumbnail( thumbnail );
		this.thumbnail.setInheritsPopupMenu( true );
		add( this.thumbnail );

		final Font small = getFont().deriveFont( 10f );
		framesField = new JTextField( String.valueOf( movieFrame.getFrames() ), 4 );
		framesField.setFont( small );
		framesField.setToolTipText( "Number of frames for the transition from the previous keyframe" );
		accelField = new JComboBox<>( Accel.NAMES );
		accelField.setSelectedIndex( Math.max( 0, Math.min( Accel.NAMES.length - 1, movieFrame.getAccel() ) ) );
		accelField.setFont( small );
		if ( movieFrame.getPosition() == 0 )
		{
			framesField.setEnabled( false );
			accelField.setEnabled( false );
		}
		final JLabel framesLabel = new JLabel( "Frames:" );
		framesLabel.setFont( small );

		final JPanel row = new JPanel( new GridLayout( 1, 2 ) );
		row.add( framesLabel );
		row.add( framesField );
		final JPanel fields = new JPanel( new GridLayout( 2, 1 ) );
		fields.add( row );
		fields.add( accelField );
		row.setInheritsPopupMenu( true );
		fields.setInheritsPopupMenu( true );
		add( fields );
		refresh( null );
	}

	/**
	 * Updates title and tooltip from the keyframe, and the thumbnail if
	 * {@code newThumbnail} is not null.
	 */
	public void refresh( final BufferedImage newThumbnail )
	{
		final List< String > tags = new ArrayList<>();
		if ( movieFrame.getTimepoint() != null )
			tags.add( "t=" + movieFrame.getTimepoint() );
		if ( movieFrame.getDisplay() != null )
			tags.add( "ch" );
		String label = String.valueOf( movieFrame.getPosition() );
		if ( !tags.isEmpty() )
			label += "  (" + String.join( ", ", tags ) + ")";
		title.setTitle( label );
		final String tip = movieFrame.getDisplay() != null
				? movieFrame.getDisplay().describe()
				: "No channel/display settings stored: the display is left as it is.";
		setToolTipText( tip );
		thumbnail.setToolTipText( tip );
		if ( newThumbnail != null )
			thumbnail.image = newThumbnail;
		repaint();
	}

	/** Reads the text fields back into the keyframe and returns it. */
	public MovieFrame getMovieFrame()
	{
		movieFrame.setAccel( accelField.getSelectedIndex() );
		try
		{
			movieFrame.setFrames( Math.max( 0, Integer.parseInt( framesField.getText().trim() ) ) );
		}
		catch ( final NumberFormatException e )
		{
			framesField.setText( String.valueOf( movieFrame.getFrames() ) );
		}
		return movieFrame;
	}

	private static class Thumbnail extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private BufferedImage image;

		Thumbnail( final BufferedImage image )
		{
			this.image = image;
			setPreferredSize( new Dimension( 90, 90 ) );
		}

		@Override
		protected void paintComponent( final Graphics g )
		{
			if ( image != null )
				g.drawImage( image, 0, 0, 90, 90, null );
		}
	}

	/** Must be called on the EDT. */
	public static BufferedImage snapshot( final JComponent c )
	{
		final int w = Math.max( 1, c.getWidth() ), h = Math.max( 1, c.getHeight() );
		final BufferedImage img = new BufferedImage( w, h, BufferedImage.TYPE_INT_RGB );
		c.paint( img.createGraphics() );
		return img;
	}
}
