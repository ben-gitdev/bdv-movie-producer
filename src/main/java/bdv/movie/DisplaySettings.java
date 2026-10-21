package bdv.movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.ConverterSetups;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.ViewerState;
import net.imglib2.type.numeric.ARGBType;

/**
 * Channel / display state stored with a keyframe: which sources and groups
 * are visible, the display mode, and each source's display range and color.
 * <p>
 * Sources and groups are matched by their index in the viewer state, so
 * settings only apply to the same dataset (same number of sources).
 * Serialized as part of the keyframe JSON.
 */
public class DisplaySettings
{
	public static class SourceSettings
	{
		/** Informative only; matching is by index. */
		String name;
		boolean active;
		double min;
		double max;
		/** ARGB; null if the source has no color. */
		Integer color;
	}

	private String displayMode;
	private int currentSource = -1;
	private int currentGroup = -1;
	private List< SourceSettings > sources = new ArrayList<>();
	private List< Boolean > activeGroups = new ArrayList<>();

	public static DisplaySettings capture( final ViewerState state, final ConverterSetups setups )
	{
		final DisplaySettings d = new DisplaySettings();
		d.displayMode = state.getDisplayMode().name();
		final List< SourceAndConverter< ? > > srcs = state.getSources();
		d.currentSource = srcs.indexOf( state.getCurrentSource() );
		for ( final SourceAndConverter< ? > soc : srcs )
		{
			final SourceSettings s = new SourceSettings();
			s.name = soc.getSpimSource().getName();
			s.active = state.isSourceActive( soc );
			final ConverterSetup cs = setups.getConverterSetup( soc );
			if ( cs != null )
			{
				s.min = cs.getDisplayRangeMin();
				s.max = cs.getDisplayRangeMax();
				if ( cs.supportsColor() )
					s.color = cs.getColor().get();
			}
			d.sources.add( s );
		}
		final List< SourceGroup > groups = state.getGroups();
		d.currentGroup = groups.indexOf( state.getCurrentGroup() );
		for ( final SourceGroup g : groups )
			d.activeGroups.add( state.isGroupActive( g ) );
		return d;
	}

	/** Whether these settings were captured from a state with the same sources. */
	public boolean matches( final ViewerState state )
	{
		return sources.size() == state.getSources().size();
	}

	/**
	 * Interpolates display ranges and colors from {@code a} ({@code t=0}) to
	 * {@code b} ({@code t=1}). Visibility, display mode and current
	 * source/group are discrete and taken from {@code a}.
	 */
	public static DisplaySettings interpolate( final DisplaySettings a, final DisplaySettings b, final double t )
	{
		if ( a.sources.size() != b.sources.size() )
			return a;
		final DisplaySettings d = new DisplaySettings();
		d.displayMode = a.displayMode;
		d.currentSource = a.currentSource;
		d.currentGroup = a.currentGroup;
		d.activeGroups = a.activeGroups;
		for ( int i = 0; i < a.sources.size(); ++i )
		{
			final SourceSettings sa = a.sources.get( i );
			final SourceSettings sb = b.sources.get( i );
			final SourceSettings s = new SourceSettings();
			s.name = sa.name;
			s.active = sa.active;
			s.min = sa.min + t * ( sb.min - sa.min );
			s.max = sa.max + t * ( sb.max - sa.max );
			s.color = ( sa.color == null || sb.color == null ) ? sa.color : lerpColor( sa.color, sb.color, t );
			d.sources.add( s );
		}
		return d;
	}

	private static int lerpColor( final int ca, final int cb, final double t )
	{
		int c = 0;
		for ( int shift = 0; shift < 32; shift += 8 )
		{
			final int va = ( ca >>> shift ) & 0xff;
			final int vb = ( cb >>> shift ) & 0xff;
			c |= ( ( int ) Math.round( va + t * ( vb - va ) ) & 0xff ) << shift;
		}
		return c;
	}

	/**
	 * Applies visibility, groups, display mode and current source/group to
	 * {@code state}. Does nothing if the sources do not match.
	 */
	public void applyVisibility( final ViewerState state )
	{
		if ( !matches( state ) )
			return;
		final List< SourceAndConverter< ? > > srcs = state.getSources();
		for ( int i = 0; i < srcs.size(); ++i )
			state.setSourceActive( srcs.get( i ), sources.get( i ).active );
		final List< SourceGroup > groups = state.getGroups();
		if ( groups.size() == activeGroups.size() )
		{
			for ( int i = 0; i < groups.size(); ++i )
				state.setGroupActive( groups.get( i ), activeGroups.get( i ) );
			if ( currentGroup >= 0 && currentGroup < groups.size() )
				state.setCurrentGroup( groups.get( currentGroup ) );
		}
		if ( currentSource >= 0 && currentSource < srcs.size() )
			state.setCurrentSource( srcs.get( currentSource ) );
		if ( displayMode != null )
		{
			try
			{
				state.setDisplayMode( DisplayMode.valueOf( displayMode ) );
			}
			catch ( final IllegalArgumentException e )
			{
				// unknown mode in JSON: keep current
			}
		}
	}

	/**
	 * Applies display ranges and colors. Converter setups are shared with the
	 * live viewer, so call this on the EDT. Does nothing if the sources do not
	 * match.
	 */
	public void applyConverters( final ViewerState state, final ConverterSetups setups )
	{
		if ( !matches( state ) )
			return;
		final List< SourceAndConverter< ? > > srcs = state.getSources();
		for ( int i = 0; i < srcs.size(); ++i )
		{
			final ConverterSetup cs = setups.getConverterSetup( srcs.get( i ) );
			if ( cs == null )
				continue;
			final SourceSettings s = sources.get( i );
			if ( cs.getDisplayRangeMin() != s.min || cs.getDisplayRangeMax() != s.max )
				cs.setDisplayRange( s.min, s.max );
			if ( s.color != null && cs.supportsColor() && cs.getColor().get() != s.color )
				cs.setColor( new ARGBType( s.color ) );
		}
	}

	/** Whether display ranges and colors are equal to those of {@code o}. */
	public boolean sameConverters( final DisplaySettings o )
	{
		if ( o == null || o.sources.size() != sources.size() )
			return false;
		for ( int i = 0; i < sources.size(); ++i )
		{
			final SourceSettings a = sources.get( i ), b = o.sources.get( i );
			if ( a.min != b.min || a.max != b.max || !Objects.equals( a.color, b.color ) )
				return false;
		}
		return true;
	}

	/** Short HTML summary of the active sources, e.g. for a tooltip. */
	public String describe()
	{
		final StringBuilder sb = new StringBuilder( "<html>" );
		if ( displayMode != null )
			sb.append( "Display mode: " ).append( displayMode.toLowerCase() ).append( "<br>" );
		int shown = 0;
		for ( final SourceSettings s : sources )
		{
			if ( !s.active )
				continue;
			if ( shown++ == 8 )
			{
				sb.append( "\u2026<br>" );
				break;
			}
			final String name = s.name == null ? "?" : s.name.replace( "&", "&amp;" ).replace( "<", "&lt;" );
			sb.append( name ).append( String.format( "  [%.4g, %.4g]", s.min, s.max ) ).append( "<br>" );
		}
		if ( shown == 0 )
			sb.append( "No active sources<br>" );
		return sb.append( "</html>" ).toString();
	}
}
