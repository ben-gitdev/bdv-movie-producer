package bdv.movie;

import java.util.ArrayList;
import java.util.List;

import bdv.viewer.animate.SimilarityTransformAnimator;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Turns a list of keyframes into the per-frame viewer transforms (and
 * timepoints and display settings) for a screen of a given size.
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
		/** null = leave the display as it is. */
		public final DisplaySettings display;

		Step( final AffineTransform3D transform, final Integer timepoint, final DisplaySettings display )
		{
			this.transform = transform;
			this.timepoint = timepoint;
			this.display = display;
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
	 * Returns a copy of {@code t} whose linear part has positive determinant.
	 * <p>
	 * Viewer transforms can contain a reflection, e.g. BigStitcher's
	 * {@code BDVPopup.initTransform} produces a negative scale for data rotated
	 * by 90 degrees. {@link SimilarityTransformAnimator} extracts a rotation
	 * quaternion and gives garbage for such transforms. BDV only renders the
	 * screen plane z = 0, so negating the screen-z row yields the identical
	 * image while making the transform a proper similarity.
	 */
	static AffineTransform3D properRotation( final AffineTransform3D t )
	{
		final AffineTransform3D a = t.copy();
		final double det =
				a.get( 0, 0 ) * ( a.get( 1, 1 ) * a.get( 2, 2 ) - a.get( 1, 2 ) * a.get( 2, 1 ) )
				- a.get( 0, 1 ) * ( a.get( 1, 0 ) * a.get( 2, 2 ) - a.get( 1, 2 ) * a.get( 2, 0 ) )
				+ a.get( 0, 2 ) * ( a.get( 1, 0 ) * a.get( 2, 1 ) - a.get( 1, 1 ) * a.get( 2, 0 ) );
		if ( det < 0 )
			for ( int c = 0; c < 4; ++c )
				a.set( -a.get( 2, c ), 2, c );
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
	 * @param easeFrames
	 *            number of frames the ease-in / ease-out curves spend
	 *            accelerating / decelerating (the whole transition if longer)
	 */
	public static List< Step > build( final List< MovieFrame > keyframes, final int width, final int height,
			final int fallbackW, final int fallbackH, final int stride, final int easeFrames )
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

		// SimilarityTransformAnimator expects transforms with the rotation
		// centre already subtracted and adds (cX, cY) back in get(), so pass
		// centre-relative transforms (as BDV's ViewerPanel does). Passing the
		// raw transforms shifts every in-between frame by (w/2, h/2).
		final double cX = width / 2.0;
		final double cY = height / 2.0;
		final AffineTransform3D[] tc = new AffineTransform3D[ n ];
		for ( int i = 0; i < n; ++i )
		{
			tc[ i ] = properRotation( t[ i ] );
			tc[ i ].set( tc[ i ].get( 0, 3 ) - cX, 0, 3 );
			tc[ i ].set( tc[ i ].get( 1, 3 ) - cY, 1, 3 );
		}

		final List< Step > steps = new ArrayList<>();
		for ( int k = 1; k < n; ++k )
		{
			final MovieFrame prev = keyframes.get( k - 1 );
			final MovieFrame cur = keyframes.get( k );
			final SimilarityTransformAnimator animator =
					new SimilarityTransformAnimator( tc[ k - 1 ], tc[ k ], cX, cY, 0 );
			final int frames = Math.max( 0, cur.getFrames() );
			for ( int d = 0; d < frames; d += Math.max( 1, stride ) )
			{
				final double lin = ( double ) d / frames;
				final double eased = Accel.apply( lin, cur.getAccel(), ( double ) easeFrames / frames );
				final AffineTransform3D tkd = animator.get( eased );
				Integer tp = null;
				if ( prev.getTimepoint() != null && cur.getTimepoint() != null )
					tp = ( int ) Math.round( prev.getTimepoint() + lin * ( cur.getTimepoint() - prev.getTimepoint() ) );
				steps.add( new Step( tkd, tp, display( prev.getDisplay(), cur.getDisplay(), eased ) ) );
			}
		}
		// land exactly on the last keyframe
		final MovieFrame last = keyframes.get( n - 1 );
		steps.add( new Step( t[ n - 1 ].copy(), last.getTimepoint(), last.getDisplay() ) );
		return steps;
	}

	/**
	 * Display for a frame of the transition {@code prev -> cur}: ranges and
	 * colors follow the same easing as the camera; visibility switches when
	 * {@code cur} is reached.
	 */
	private static DisplaySettings display( final DisplaySettings prev, final DisplaySettings cur, final double eased )
	{
		if ( prev == null )
			return null;
		return cur == null ? prev : DisplaySettings.interpolate( prev, cur, eased );
	}
}
