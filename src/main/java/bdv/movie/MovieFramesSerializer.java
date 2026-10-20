package bdv.movie;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import net.imglib2.realtransform.AffineTransform3D;

/** JSON import/export, compatible with files written by the original fork. */
public class MovieFramesSerializer
{
	private static class AffineAdapter implements JsonSerializer< AffineTransform3D >, JsonDeserializer< AffineTransform3D >
	{
		@Override
		public AffineTransform3D deserialize( final JsonElement json, final Type type, final JsonDeserializationContext ctx )
		{
			final double[] data = ctx.deserialize( json, double[].class );
			final AffineTransform3D t = new AffineTransform3D();
			t.set( data );
			return t;
		}

		@Override
		public JsonElement serialize( final AffineTransform3D src, final Type type, final JsonSerializationContext ctx )
		{
			final double[] data = new double[ 12 ];
			src.toArray( data );
			return ctx.serialize( data );
		}
	}

	private static Gson gson()
	{
		return new GsonBuilder()
				.setPrettyPrinting()
				.serializeSpecialFloatingPointValues()
				.registerTypeAdapter( AffineTransform3D.class, new AffineAdapter() )
				.create();
	}

	public static void save( final List< MovieFrame > list, final File file ) throws IOException
	{
		try (Writer w = Files.newBufferedWriter( file.toPath(), StandardCharsets.UTF_8 ))
		{
			gson().toJson( list, w );
		}
	}

	public static List< MovieFrame > load( final File file ) throws IOException
	{
		try (Reader r = Files.newBufferedReader( file.toPath(), StandardCharsets.UTF_8 ))
		{
			return gson().fromJson( r, new TypeToken< List< MovieFrame > >() {}.getType() );
		}
	}
}
