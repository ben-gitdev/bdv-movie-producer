package bdv.movie;

import java.util.ArrayList;
import java.util.List;

import bdv.viewer.animate.SimilarityTransformAnimator;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Turns a list of keyframes into the per-frame viewer transforms (and
 * timepoints) for a screen of a given size.
 * <p>
 * Viewer transforms map to screen pixels, so a transform captured in a
 * 900x600 window shows a different region when rendered at 1920x1080.
 * Each keyframe is first re-mapped from the canvas it was captured on to
 * the target size (keeping the view centre and scaling by width, the same
 * convention as BDV's built-in "Record Movie").
 */
public class MovieSequence
{
	public static class Step
	{
		public final AffineTransform3D transform;
		public final Integer timepoint;

		Step( final AffineTransform3D transform, final Integer timepoint )
		{
			this.transform = transform;
			this.timepoint = timepoint;
		}
	}

	/** Re-map a screen transform from a (fromW x fromH) canvas to a (toW x toH) canvas. */
	public static AffineTransform3D remap( final AffineTransform3D t, final int fromW, final int fromH, final int toW, final int toH )
	{
		final AffineTransform3D a = t.copy();
		if ( fromW <= 0 || fromH <= 0 || ( fromW == toW && fromH == toH ) )
			return a;
		a.set( a.get( 0, 3 ) - fromW / 2.0, 0, 3 );
		a.set( a.get( 1, 3 ) - fromH / 2.0, 1, 3 );
		a.scale( ( double ) toW / fromW );
		a.set( a.get( 0, 3 ) + toW / 2.0, 0, 3 );
		a.set( a.get( 1, 3 ) + toH / 2.0, 1, 3 );
		return a;
	}

	/**
	 * @param keyframes
	 *            at least two keyframes
	 * @param width,height
	 *            target screen size
	 * @param fallbackW,fallbackH
	 *            canvas size assumed for keyframes from old JSON files
	 * @param stride
	 *            1 for full output, n to take every n-th frame (preview)
	 */
	public static List< Step > build( final List< MovieFrame > keyframes, final int width, final int height,
			final int fallbackW, final int fallbackH, final int stride )
	{
		final int n = keyframes.size();
		final AffineTransform3D[] t = new AffineTransform3D[ n ];
		for ( int i = 0; i < n; ++i )
		{
			final MovieFrame f = keyframes.get( i );
			final int cw = f.getCanvasWidth() > 0 ? f.getCanvasWidth() : fallbackW;
			final int ch = f.getCanvasHeight() > 0 ? f.getCanvasHeight() : fallbackH;
			t[ i ] = remap( f.getTransform(), cw, ch, width, height );
		}

		final List< Step > steps = new ArrayList<>();
		for ( int k = 1; k < n; ++k )
		{
			final MovieFrame prev = keyframes.get( k - 1 );
			final MovieFrame cur = keyframes.get( k );
			final SimilarityTransformAnimator animator =
					new SimilarityTransformAnimator( t[ k - 1 ], t[ k ], width / 2.0, height / 2.0, 0 );
			final int frames = Math.max( 0, cur.getFrames() );
			for ( int d = 0; d < frames; d += Math.max( 1, stride ) )
			{
				final double lin = ( double ) d / frames;
				final AffineTransform3D tkd = animator.get( Accel.apply( lin, cur.getAccel() ) );
				Integer tp = null;
				if ( prev.getTimepoint() != null && cur.getTimepoint() != null )
					tp = ( int ) Math.round( prev.getTimepoint() + lin * ( cur.getTimepoint() - prev.getTimepoint() ) );
				steps.add( new Step( tkd, tp ) );
			}
		}
		// land exactly on the last keyframe
		steps.add( new Step( t[ n - 1 ].copy(), keyframes.get( n - 1 ).getTimepoint() ) );
		return steps;
	}
}
