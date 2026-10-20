package bdv.movie;

/**
 * Easing curves for keyframe transitions.
 * Adapted from org.janelia.saalfeldlab.hotknife.VNCMovie (Stephan Saalfeld, GPL-2).
 */
public final class Accel
{
	public static final String[] NAMES = {
			"symmetric", "slow start", "slow end",
			"soft symmetric", "soft slow start", "soft slow end" };

	private Accel() {}

	private static double cos( final double x )
	{
		return 0.5 - 0.5 * Math.cos( Math.PI * x );
	}

	/** Maps linear t in [0,1] to eased t in [0,1]. */
	public static double apply( final double t, final int type )
	{
		switch ( type )
		{
		case 1: return cos( t * t );
		case 2: return 1.0 - cos( Math.pow( 1.0 - t, 2 ) );
		case 3: return cos( cos( t ) );
		case 4: return cos( cos( t * t ) );
		case 5: return 1.0 - cos( cos( Math.pow( 1.0 - t, 2 ) ) );
		default: return cos( t );
		}
	}
}
