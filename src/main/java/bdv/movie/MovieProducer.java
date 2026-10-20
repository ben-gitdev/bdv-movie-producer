package bdv.movie;

import java.awt.AWTEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import bdv.viewer.ViewerFrame;

/** Attaches the movie producer (Tools menu item + F7) to a BDV window. */
public final class MovieProducer
{
	public static final String ACTION_NAME = "produce movie (keyframes)";
	public static final String KEY = "F7";
	private static final String CLIENT_KEY = MovieProducer.class.getName();

	private static AWTEventListener autoAttachListener;

	private MovieProducer() {}

	/**
	 * Installs (once per JVM) a listener that attaches the movie producer to
	 * every BDV window when it is opened or activated, and attaches to the
	 * BDV windows that are already open. Safe to call repeatedly from any
	 * thread; called both from {@link MovieProducerAutoAttach} and from
	 * {@link MovieProducerKeys}, so it runs even if one hook is never hit.
	 */
	public static synchronized void installAutoAttach()
	{
		if ( autoAttachListener != null || GraphicsEnvironment.isHeadless() )
			return;
		autoAttachListener = event -> {
			final int id = event.getID();
			if ( ( id == WindowEvent.WINDOW_OPENED || id == WindowEvent.WINDOW_ACTIVATED )
					&& event.getSource() instanceof ViewerFrame )
				attachLater( ( ViewerFrame ) event.getSource() );
		};
		Toolkit.getDefaultToolkit().addAWTEventListener( autoAttachListener, AWTEvent.WINDOW_EVENT_MASK );
		// visible (not just displayable): BDV sets the menu bar before showing the window
		for ( final Window w : Window.getWindows() )
			if ( w instanceof ViewerFrame && w.isVisible() )
				attachLater( ( ViewerFrame ) w );
	}

	private static void attachLater( final ViewerFrame frame )
	{
		SwingUtilities.invokeLater( () -> {
			try
			{
				attach( frame );
			}
			catch ( final Throwable t )
			{
				t.printStackTrace(); // never break BDV because of us
			}
		} );
	}

	/** Idempotent: calling it twice on the same window does nothing extra. */
	public static synchronized ProduceMovieDialog attach( final ViewerFrame frame )
	{
		final JRootPane root = frame.getRootPane();
		final Object existing = root.getClientProperty( CLIENT_KEY );
		if ( existing instanceof ProduceMovieDialog )
			return ( ProduceMovieDialog ) existing;

		final ProduceMovieDialog dialog = new ProduceMovieDialog( frame, frame.getViewerPanel() );
		root.putClientProperty( CLIENT_KEY, dialog );

		final Runnable toggle = () -> dialog.setVisible( !dialog.isVisible() );

		final Actions actions = new Actions( new InputTriggerConfig(), "bdv" );
		actions.runnableAction( toggle, ACTION_NAME, KEY );
		actions.install( frame.getKeybindings(), "movie-producer" );

		final JMenuBar bar = frame.getJMenuBar();
		if ( bar != null )
		{
			JMenu tools = null;
			for ( int i = 0; i < bar.getMenuCount(); ++i )
				if ( bar.getMenu( i ) != null && "Tools".equals( bar.getMenu( i ).getText() ) )
					tools = bar.getMenu( i );
			if ( tools == null )
			{
				tools = new JMenu( "Tools" );
				bar.add( tools );
			}
			final JMenuItem item = new JMenuItem( "Produce Movie (keyframes)" );
			item.setAccelerator( KeyStroke.getKeyStroke( KEY ) );
			// accelerator is display-only; the key itself is handled by the BDV keybinding above
			item.addActionListener( e -> toggle.run() );
			tools.add( item );
			bar.revalidate();
		}
		return dialog;
	}

	/** The most recently active visible BDV window, or null. */
	public static ViewerFrame findViewerFrame()
	{
		ViewerFrame best = null;
		for ( final Window w : Window.getWindows() )
		{
			if ( w instanceof ViewerFrame && w.isVisible() )
			{
				if ( w.isFocused() || w.isActive() )
					return ( ViewerFrame ) w;
				best = ( ViewerFrame ) w;
			}
		}
		return best;
	}

}
