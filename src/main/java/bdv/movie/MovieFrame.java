package bdv.movie;

import net.imglib2.realtransform.AffineTransform3D;

/**
 * One keyframe. Field names are kept identical to the original
 * bigdataviewer-core-movie fork so its exported JSON files still load.
 * <p>
 * {@code frames} and {@code accel} describe the transition from the
 * previous keyframe to this one.
 */
public class MovieFrame
{
	public static final int DEFAULT_FRAMES = 120;

	private AffineTransform3D transform;
	private int position;
	private int frames;
	private int accel;

	/** Added: viewer canvas size when the keyframe was captured (0 = unknown, old JSON). */
	private int canvasWidth;
	private int canvasHeight;

	/** Added: timepoint when captured (null = old JSON, keep current timepoint). */
	private Integer timepoint;

	public MovieFrame( final int position, final AffineTransform3D transform, final int canvasWidth, final int canvasHeight, final Integer timepoint )
	{
		this.position = position;
		this.transform = transform;
		this.frames = position == 0 ? 0 : DEFAULT_FRAMES;
		this.accel = 0;
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		this.timepoint = timepoint;
	}

	public AffineTransform3D getTransform() { return transform; }
	public void setTransform( final AffineTransform3D transform ) { this.transform = transform; }
	public int getPosition() { return position; }
	public void setPosition( final int position ) { this.position = position; }
	public int getFrames() { return frames; }
	public void setFrames( final int frames ) { this.frames = frames; }
	public int getAccel() { return accel; }
	public void setAccel( final int accel ) { this.accel = accel; }
	public int getCanvasWidth() { return canvasWidth; }
	public int getCanvasHeight() { return canvasHeight; }
	public Integer getTimepoint() { return timepoint; }
	public void setTimepoint( final Integer timepoint ) { this.timepoint = timepoint; }
}
