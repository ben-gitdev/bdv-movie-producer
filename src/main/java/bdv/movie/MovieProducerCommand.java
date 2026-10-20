package bdv.movie;

import javax.swing.SwingUtilities;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import bdv.viewer.ViewerFrame;
import ij.IJ;

/** Plugins > BigDataViewer > Movie Producer (keyframes) */
@Plugin( type = Command.class, menuPath = "Plugins>BigDataViewer>Movie Producer (keyframes)" )
public class MovieProducerCommand implements Command
{
	@Override
	public void run()
	{
		SwingUtilities.invokeLater( () -> {
			final ViewerFrame frame = MovieProducer.findViewerFrame();
			if ( frame == null )
			{
				IJ.error( "Movie Producer", "Open a BigDataViewer window first\n(e.g. Plugins > BigDataViewer > Open XML/HDF5)." );
				return;
			}
			final ProduceMovieDialog dialog = MovieProducer.attach( frame );
			dialog.setVisible( true );
			dialog.toFront();
		} );
	}
}
