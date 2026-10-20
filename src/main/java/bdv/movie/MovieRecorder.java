package bdv.movie;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

import javax.imageio.ImageIO;

import bdv.cache.CacheControl;
import bdv.viewer.BasicViewerState;
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

	/** @return number of frames written */
	public static int record(
			final ViewerPanel viewer,
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
		final MultiResolutionRenderer renderer = new MultiResolutionRenderer(
				target, () -> {}, new double[] { 1 }, 0, threads, null, false, new CacheControl.Dummy() );

		final ScaleBarOverlayRenderer scalebar = scaleBar ? new ScaleBarOverlayRenderer() : null;
		final MultiBoxOverlayRenderer box = boxOverlay ? new MultiBoxOverlayRenderer( width, height ) : null;
		final int nTimepoints = renderState.getNumTimepoints();

		int i = 0;
		for ( final MovieSequence.Step step : steps )
		{
			if ( cancelled.getAsBoolean() )
				break;
			renderState.setViewerTransform( step.transform );
			if ( step.timepoint != null && step.timepoint >= 0 && step.timepoint < nTimepoints )
				renderState.setCurrentTimepoint( step.timepoint );

			renderer.requestRepaint();
			renderer.paint( renderState );

			final BufferedImage bi = target.renderResult.getBufferedImage();
			if ( scalebar != null || box != null )
			{
				final Graphics2D g2 = bi.createGraphics();
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
				g2.dispose();
			}
			ImageIO.write( bi, "png", new File( dir, String.format( "img-%05d.png", i ) ) );
			++i;
			progress.accept( ( double ) i / steps.size() );
		}
		return i;
	}
}
