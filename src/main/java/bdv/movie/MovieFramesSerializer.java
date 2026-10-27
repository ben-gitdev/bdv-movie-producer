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
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import net.imglib2.realtransform.AffineTransform3D;

/**
 * JSON import/export. Files are written as
 * {@code {"easeFrames": n, "keyframes": [...]}}; a bare keyframe list (files
 * from the original fork and earlier versions) is still read.
 */
public class MovieFramesSerializer
{
	private static final Type LIST_TYPE = new TypeToken< List< MovieFrame > >() {}.getType();

	public static class Movie
	{
		public final List< MovieFrame > keyframes;

		/** null = not stored (bare keyframe list). */
		public final Integer easeFrames;

		Movie( final List< MovieFrame > keyframes, final Integer easeFrames )
		{
			this.keyframes = keyframes;
			this.easeFrames = easeFrames;
		}
	}

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

	public static void save( final List< MovieFrame > list, final int easeFrames, final File file ) throws IOException
	{
		final Gson gson = gson();
		final JsonObject root = new JsonObject();
		root.addProperty( "easeFrames", easeFrames );
		root.add( "keyframes", gson.toJsonTree( list, LIST_TYPE ) );
		try (Writer w = Files.newBufferedWriter( file.toPath(), StandardCharsets.UTF_8 ))
		{
			gson.toJson( root, w );
		}
	}

	public static Movie load( final File file ) throws IOException
	{
		final Gson gson = gson();
		final JsonElement root;
		try (Reader r = Files.newBufferedReader( file.toPath(), StandardCharsets.UTF_8 ))
		{
			root = gson.fromJson( r, JsonElement.class );
		}
		if ( root == null )
			throw new IOException( "empty file" );
		if ( root.isJsonArray() )
			return new Movie( gson.fromJson( root, LIST_TYPE ), null );
		final JsonObject o = root.getAsJsonObject();
		if ( !o.has( "keyframes" ) )
			throw new IOException( "no \"keyframes\" found" );
		final Integer easeFrames = o.has( "easeFrames" ) ? o.get( "easeFrames" ).getAsInt() : null;
		return new Movie( gson.fromJson( o.get( "keyframes" ), LIST_TYPE ), easeFrames );
	}
}
