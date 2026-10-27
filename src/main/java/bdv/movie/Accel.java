package bdv.movie;

/**
 * Easing curves for keyframe transitions.
 * Adapted from org.janelia.saalfeldlab.hotknife.VNCMovie (Stephan Saalfeld, GPL-2).
 */
public final class Accel
{
	public static final String[] NAMES = {
			"symmetric", "ease-in", "ease-out",
			"soft symmetric", "soft slow start", "soft slow end" };

	private Accel() {}

	private static double cos( final double x )
	{
		return 0.5 - 0.5 * Math.cos( Math.PI * x );
	}

	/** Scale so that easeIn(1) = 1: first half reaches c(0.5) = 0.5, second half adds slope pi/2 over 0.5. */
	private static final double EASE_IN_SCALE = 1.0 / ( 0.5 + Math.PI / 4 );

	/**
	 * Accelerates along c(t) to top speed at t = 0.5 (where c is steepest),
	 * then keeps that speed to the end.
	 */
	private static double easeIn( final double t )
	{
		if ( t < 0.5 )
			return EASE_IN_SCALE * cos( t );
		return EASE_IN_SCALE * ( 0.5 + 0.5 * Math.PI * ( t - 0.5 ) );
	}

	/** Maps linear t in [0,1] to eased t in [0,1]. */
	public static double apply( final double t, final int type )
	{
		switch ( type )
		{
		case 1: return easeIn( t );
		case 2: return 1.0 - easeIn( 1.0 - t );
		case 3: return cos( cos( t ) );
		case 4: return cos( cos( t * t ) );
		case 5: return 1.0 - cos( cos( Math.pow( 1.0 - t, 2 ) ) );
		default: return cos( t );
		}
	}
}
