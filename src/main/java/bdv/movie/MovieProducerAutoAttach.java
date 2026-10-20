package bdv.movie;

import java.awt.AWTEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;

import javax.swing.SwingUtilities;

import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import bdv.viewer.ViewerFrame;

/**
 * Started by Fiji at launch. Watches for BigDataViewer windows being opened
 * (from Fiji menus, BigStitcher, scripts, ...) and adds the movie producer
 * to each one, so F7 / Tools menu work without running the command first.
 */
@Plugin( type = Service.class )
public class MovieProducerAutoAttach extends AbstractService
{
	private AWTEventListener listener;

	@Override
	public void initialize()
	{
		if ( GraphicsEnvironment.isHeadless() )
			return;
		listener = event -> {
			if ( event.getID() == WindowEvent.WINDOW_OPENED )
			{
				final Object src = event.getSource();
				if ( src instanceof ViewerFrame )
					SwingUtilities.invokeLater( () -> {
						try
						{
							MovieProducer.attach( ( ViewerFrame ) src );
						}
						catch ( final Throwable t )
						{
							t.printStackTrace(); // never break BDV because of us
						}
					} );
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener( listener, AWTEvent.WINDOW_EVENT_MASK );
	}

	@Override
	public void dispose()
	{
		if ( listener != null )
			Toolkit.getDefaultToolkit().removeAWTEventListener( listener );
	}
}
