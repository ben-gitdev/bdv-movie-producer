package bdv.movie;

import java.awt.Window;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import bdv.viewer.ViewerFrame;

/** Attaches the movie producer (Tools menu item + F7) to a BDV window. */
public final class MovieProducer
{
	public static final String ACTION_NAME = "produce movie (keyframes)";
	public static final String KEY = "F7";
	private static final String CLIENT_KEY = MovieProducer.class.getName();

	private MovieProducer() {}

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
