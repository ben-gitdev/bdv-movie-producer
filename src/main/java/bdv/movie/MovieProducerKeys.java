package bdv.movie;

import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;

import bdv.KeyConfigContexts;
import bdv.KeyConfigScopes;

/**
 * Lists the movie producer shortcut in BDV's keymap (Preferences &gt; Keymap).
 * <p>
 * BDV instantiates all {@code CommandDescriptionProvider}s while constructing
 * each viewer, before its window is shown. We use that as a second,
 * BDV-driven hook to install the auto-attach listener, independent of
 * whether Fiji starts {@link MovieProducerAutoAttach}.
 */
@Plugin( type = CommandDescriptionProvider.class )
public class MovieProducerKeys extends CommandDescriptionProvider
{
	public MovieProducerKeys()
	{
		super( KeyConfigScopes.BIGDATAVIEWER, KeyConfigContexts.BIGDATAVIEWER );
		try
		{
			MovieProducer.installAutoAttach();
		}
		catch ( final Throwable t )
		{
			t.printStackTrace(); // never break BDV because of us
		}
	}

	@Override
	public void getCommandDescriptions( final CommandDescriptions descriptions )
	{
		descriptions.add( MovieProducer.ACTION_NAME, new String[] { MovieProducer.KEY },
				"Show/hide the keyframe Movie Producer dialog." );
	}
}
