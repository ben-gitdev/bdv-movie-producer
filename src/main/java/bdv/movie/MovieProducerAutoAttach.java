package bdv.movie;

import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

/**
 * Started with Fiji's SciJava context. Installs the listener that adds the
 * movie producer to every BigDataViewer window (from Fiji menus, BigStitcher,
 * scripts, ...), so F7 / Tools menu work without running the command first.
 * <p>
 * The listener is JVM-global and deliberately not removed in
 * {@code dispose()}: other code may create and dispose temporary contexts.
 */
@Plugin( type = Service.class )
public class MovieProducerAutoAttach extends AbstractService
{
	@Override
	public void initialize()
	{
		MovieProducer.installAutoAttach();
	}
}
