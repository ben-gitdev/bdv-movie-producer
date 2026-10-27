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
	 * Accelerates along c(t) (or c(c(t)) if {@code soft}) to top speed at
	 * t = 0.5, where both are steepest with value 0.5, then keeps that speed
	 * to the end. Scaled so that easeIn(1) = 1.
	 */
	private static double easeIn( final double t, final boolean soft )
	{
		final double slope = soft ? Math.PI * Math.PI / 4 : Math.PI / 2;
		final double scale = 1.0 / ( 0.5 + 0.5 * slope );
		if ( t < 0.5 )
			return scale * ( soft ? cos( cos( t ) ) : cos( t ) );
		return scale * ( 0.5 + slope * ( t - 0.5 ) );
	}

	/** Maps linear t in [0,1] to eased t in [0,1]. */
	public static double apply( final double t, final int type )
	{
		switch ( type )
		{
		case 1: return easeIn( t, false );
		case 2: return 1.0 - easeIn( 1.0 - t, false );
		case 3: return cos( cos( t ) );
		case 4: return easeIn( t, true );
		case 5: return 1.0 - easeIn( 1.0 - t, true );
		default: return cos( t );
		}
	}
}
