package bdv.movie;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import bdv.cache.CacheControl;
import bdv.viewer.BasicViewerState;
import bdv.viewer.ConverterSetups;
import bdv.viewer.Interpolation;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import bdv.viewer.overlay.MultiBoxOverlayRenderer;
import bdv.viewer.overlay.ScaleBarOverlayRenderer;
import bdv.viewer.render.MultiResolutionRenderer;
import bdv.viewer.render.RenderTarget;
import bdv.viewer.render.awt.BufferedImageRenderResult;

/**
 * Renders a keyframe sequence off-screen to a PNG sequence.
 * Works on a snapshot of the viewer state, so the live BDV window is not
 * moved or resized while recording (unlike the original fork).
 */
public class MovieRecorder
{
	private static class Target implements RenderTarget< BufferedImageRenderResult >
	{
		final BufferedImageRenderResult renderResult = new BufferedImageRenderResult();
		final int width, height;

		Target( final int width, final int height ) { this.width = width; this.height = height; }

		@Override public BufferedImageRenderResult getReusableRenderResult() { return renderResult; }
		@Override public BufferedImageRenderResult createRenderResult() { return new BufferedImageRenderResult(); }
		@Override public void setRenderResult( final BufferedImageRenderResult r ) {}
		@Override public int getWidth() { return width; }
		@Override public int getHeight() { return height; }
	}

	/** Upper bound on PNG writer threads (each holds one frame in memory). */
	private static final int MAX_WRITERS = 4;

	/** @return number of frames written */
	public static int record(
			final ViewerPanel viewer,
			final ConverterSetups setups,
			final List< MovieFrame > keyframes,
			final int width,
			final int height,
			final boolean scaleBar,
			final boolean boxOverlay,
			final File dir,
			final DoubleConsumer progress,
			final BooleanSupplier cancelled ) throws IOException
	{
		final ViewerState renderState = new BasicViewerState( viewer.state().snapshot() );
		renderState.setInterpolation( Interpolation.NLINEAR );

		final int canvasW = viewer.getDisplay().getWidth();
		final int canvasH = viewer.getDisplay().getHeight();
		final List< MovieSequence.Step > steps = MovieSequence.build( keyframes, width, height, canvasW, canvasH, 1 );

		final Target target = new Target( width, height );
		final int threads = Math.max( 1, Runtime.getRuntime().availableProcessors() );
		final ForkJoinPool renderPool = new ForkJoinPool( threads );
		final MultiResolutionRenderer renderer = new MultiResolutionRenderer(
				target, () -> {}, new double[] { 1 }, 0, threads, renderPool, false, new CacheControl.Dummy() );

		final ScaleBarOverlayRenderer scalebar = scaleBar ? new ScaleBarOverlayRenderer() : null;
		final MultiBoxOverlayRenderer box = boxOverlay ? new MultiBoxOverlayRenderer( width, height ) : null;
		final int nTimepoints = renderState.getNumTimepoints();

		// ImageIO's PNG encoder is single-threaded and slow at high resolution
		// (~0.5 s for a 4K frame). Encode on a few writer threads while the
		// next frame renders. Frame buffers are recycled through a bounded queue,
		// which also limits the number of frames in flight (memory).
		final int writers = Math.max( 1, Math.min( MAX_WRITERS, threads / 2 ) );
		final ExecutorService writePool = Executors.newFixedThreadPool( writers );
		final BlockingQueue< BufferedImage > freeFrames = new ArrayBlockingQueue<>( writers + 1 );
		for ( int k = 0; k < writers + 1; ++k )
			freeFrames.add( new BufferedImage( width, height, BufferedImage.TYPE_INT_RGB ) );
		final AtomicInteger written = new AtomicInteger();
		final AtomicReference< Throwable > writeError = new AtomicReference<>();

		// Visibility is applied to our snapshot only, but display ranges and
		// colors live in converters shared with the viewer: they are changed
		// on the EDT while recording and restored afterwards.
		final DisplaySettings[] original = new DisplaySettings[ 1 ];
		DisplaySettings appliedConverters = null;

		int submitted = 0;
		try
		{
			onEdt( () -> original[ 0 ] = DisplaySettings.capture( renderState, setups ) );
			appliedConverters = original[ 0 ];

			for ( final MovieSequence.Step step : steps )
			{
				if ( cancelled.getAsBoolean() || writeError.get() != null )
					break;
				renderState.setViewerTransform( step.transform );
				if ( step.timepoint != null && step.timepoint >= 0 && step.timepoint < nTimepoints )
					renderState.setCurrentTimepoint( step.timepoint );
				if ( step.display != null )
				{
					step.display.applyVisibility( renderState );
					if ( !step.display.sameConverters( appliedConverters ) )
					{
						onEdt( () -> step.display.applyConverters( renderState, setups ) );
						appliedConverters = step.display;
					}
				}

				renderer.requestRepaint();
				renderer.paint( renderState );

				final BufferedImage frame = freeFrames.take();

				// The render result is ARGB and empty space is fully transparent,
				// which most viewers/encoders show as white. Flatten onto black,
				// like the live viewer does.
				final Graphics2D g2 = frame.createGraphics();
				g2.setColor( Color.BLACK );
				g2.fillRect( 0, 0, width, height );
				g2.drawImage( target.renderResult.getBufferedImage(), 0, 0, null );
				if ( scalebar != null || box != null )
				{
					g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
					g2.setClip( 0, 0, width, height );
					if ( scalebar != null )
					{
						scalebar.setViewerState( renderState );
						scalebar.paint( g2 );
					}
					if ( box != null )
					{
						box.setViewerState( renderState );
						box.paint( g2 );
					}
				}
				g2.dispose();

				final File file = new File( dir, String.format( "img-%05d.png", submitted ) );
				++submitted;
				writePool.execute( () -> {
					try
					{
						if ( writeError.get() == null )
						{
							ImageIO.write( frame, "png", file );
							progress.accept( ( double ) written.incrementAndGet() / steps.size() );
						}
					}
					catch ( final Throwable t )
					{
						writeError.compareAndSet( null, t );
					}
					finally
					{
						freeFrames.add( frame );
					}
				} );
			}
		}
		catch ( final InterruptedException e )
		{
			Thread.currentThread().interrupt();
		}
		finally
		{
			if ( original[ 0 ] != null && appliedConverters != original[ 0 ] )
				SwingUtilities.invokeLater( () -> original[ 0 ].applyConverters( renderState, setups ) );

			// let frames that are already rendered finish writing
			writePool.shutdown();
			try
			{
				writePool.awaitTermination( 1, TimeUnit.DAYS );
			}
			catch ( final InterruptedException e )
			{
				Thread.currentThread().interrupt();
			}
			renderPool.shutdown();
		}

		final Throwable err = writeError.get();
		if ( err instanceof IOException )
			throw ( IOException ) err;
		if ( err != null )
			throw new IOException( err );
		return submitted;
	}

	private static void onEdt( final Runnable r ) throws InterruptedException, IOException
	{
		try
		{
			SwingUtilities.invokeAndWait( r );
		}
		catch ( final InvocationTargetException e )
		{
			throw new IOException( e.getCause() );
		}
	}
}
