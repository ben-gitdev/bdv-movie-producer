package bdv.movie;

import java.util.List;

import javax.swing.SwingUtilities;

import bdv.viewer.ViewerPanel;

/**
 * Plays a down-sampled preview in the live viewer.
 * Replaces the original Thread.stop()/suspend()/resume() calls, which throw
 * UnsupportedOperationException on Java 20+ (current Fiji ships Java 21).
 */
public class PreviewPlayer
{
	private final ViewerPanel viewer;
	private volatile Thread thread;

	public PreviewPlayer( final ViewerPanel viewer )
	{
		this.viewer = viewer;
	}

	public synchronized void play( final List< MovieFrame > keyframes, final int stride, final int sleepMs )
	{
		stop();
		final int w = viewer.getDisplay().getWidth();
		final int h = viewer.getDisplay().getHeight();
		final List< MovieSequence.Step > steps = MovieSequence.build( keyframes, w, h, w, h, stride );
		final int nTimepoints = viewer.state().getNumTimepoints();
		final Thread t = new Thread( () -> {
			try
			{
				for ( final MovieSequence.Step s : steps )
				{
					if ( Thread.currentThread().isInterrupted() )
						return;
					SwingUtilities.invokeLater( () -> {
						viewer.state().setViewerTransform( s.transform );
						if ( s.timepoint != null && s.timepoint >= 0 && s.timepoint < nTimepoints )
							viewer.state().setCurrentTimepoint( s.timepoint );
					} );
					Thread.sleep( Math.max( 1, sleepMs ) );
				}
			}
			catch ( final InterruptedException e )
			{
				// stopped
			}
		}, "BDV movie preview" );
		t.setDaemon( true );
		thread = t;
		t.start();
	}

	public synchronized void stop()
	{
		final Thread t = thread;
		if ( t != null )
			t.interrupt();
		thread = null;
	}
}
