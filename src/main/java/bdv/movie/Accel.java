package bdv.movie;

/**
 * Easing curves for keyframe transitions.
 * Adapted from org.janelia.saalfeldlab.hotknife.VNCMovie (Stephan Saalfeld, GPL-2).
 */
public final class Accel
{
	public static final String[] NAMES = {
			"symmetric", "ease-in", "ease-out",
			"soft symmetric", "soft ease-in", "soft ease-out" };

	private Accel() {}

	private static double cos( final double x )
	{
		return 0.5 - 0.5 * Math.cos( Math.PI * x );
	}

	/**
	 * Accelerates during [0, p] along the first half of c (or c∘c if
	 * {@code soft}), which ends at its steepest point with value 0.5, then
	 * keeps that top speed to the end. Scaled so that easeIn(1) = 1.
	 */
	private static double easeIn( final double t, final boolean soft, final double p )
	{
		if ( p <= 0 )
			return t;
		final double slope = soft ? Math.PI * Math.PI / 4 : Math.PI / 2;
		final double scale = 1.0 / ( 0.5 + slope * ( 1 - p ) / ( 2 * p ) );
		if ( t < p )
			return scale * half( t / ( 2 * p ), soft );
		return scale * ( 0.5 + slope * ( t - p ) / ( 2 * p ) );
	}

	/**
	 * Accelerates during [0, p] and decelerates during [1 - p, 1] like
	 * {@link #easeIn}, at constant top speed in between. With p = 0.5 this is
	 * c(t) (or c(c(t)) if {@code soft}).
	 */
	private static double easeInOut( final double t, final boolean soft, final double p )
	{
		if ( p <= 0 )
			return t;
		final double slope = soft ? Math.PI * Math.PI / 4 : Math.PI / 2;
		final double scale = 1.0 / ( 1 + slope * ( 1 - 2 * p ) / ( 2 * p ) );
		if ( t < p )
			return scale * half( t / ( 2 * p ), soft );
		if ( t > 1 - p )
			return 1.0 - scale * half( ( 1 - t ) / ( 2 * p ), soft );
		return scale * ( 0.5 + slope * ( t - p ) / ( 2 * p ) );
	}

	/** c(x), or c(c(x)) if {@code soft}; used for x in [0, 0.5]. */
	private static double half( final double x, final boolean soft )
	{
		return soft ? cos( cos( x ) ) : cos( x );
	}

	/**
	 * Maps linear t in [0,1] to eased t in [0,1].
	 *
	 * @param easeFraction
	 *            fraction of the transition spent accelerating and/or
	 *            decelerating; clamped to [0,1] for ease-in / ease-out and to
	 *            [0,0.5] (each end) for the symmetric curves
	 */
	public static double apply( final double t, final int type, final double easeFraction )
	{
		final double p = Math.max( 0, Math.min( 1, easeFraction ) );
		final double q = Math.min( 0.5, p );
		switch ( type )
		{
		case 1: return easeIn( t, false, p );
		case 2: return 1.0 - easeIn( 1.0 - t, false, p );
		case 3: return easeInOut( t, true, q );
		case 4: return easeIn( t, true, p );
		case 5: return 1.0 - easeIn( 1.0 - t, true, p );
		default: return easeInOut( t, false, q );
		}
	}
}
