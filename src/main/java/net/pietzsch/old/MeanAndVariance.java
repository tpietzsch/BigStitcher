package net.pietzsch.old;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import java.util.Arrays;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.pietzsch.movingsum.SeparableMovingSum;

// Compare results of SeparableMovingSum and neighborhood-based reference implementation
public class MeanAndVariance
{
	public static void main( String[] args )
	{
		ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/imglib/imglib2-introductory-workshop/target/classes/blobs.tif" );
//		ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/img1.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img = copyToArrayImg( ImageJFunctions.wrapByte( imp ) );
		Bdv bdv = BdvFunctions.show( img, "img", Bdv.options().is2D() );

		final int blockSize = 21;
		final int n = img.numDimensions();

		Views.interval( img, Intervals.createMinSize( img.dimension( 0 ) / 2, img.dimension( 1 ) / 2, blockSize, blockSize ) ).forEach( t -> t.set( 0 ) );

		final RandomAccessibleInterval< UnsignedLongType > sum = new ArrayImgFactory<>( new UnsignedLongType() ).create( img );
		final RandomAccessibleInterval< UnsignedLongType > sumsqu = new ArrayImgFactory<>( new UnsignedLongType() ).create( img );
		final int[] windowSize = new int[ n ];
		Arrays.fill( windowSize, blockSize );
		SeparableMovingSum.convolve( windowSize,
				Converters.convert(
						Views.extendBorder( img ),
						RealTypeConverters.getConverter( new UnsignedByteType(), new UnsignedLongType() ),
						new UnsignedLongType() ),
				sum );
		SeparableMovingSum.convolve( windowSize,
				Converters.convert(
						Views.extendBorder( img ),
						( i, o ) -> {
							final long v = i.get();
							o.set( v * v );
						},
						new UnsignedLongType() ),
				sumsqu );

//		BdvFunctions.show( sum, "sum", Bdv.options().addTo( bdv ) );
//		BdvFunctions.show( sumsqu, "sumsqu", Bdv.options().addTo( bdv ) );

		final RandomAccessibleInterval< UnsignedByteType > mean = new ArrayImgFactory<>( new UnsignedByteType() ).create( img );
		final RandomAccessibleInterval< UnsignedShortType > variance = new ArrayImgFactory<>( new UnsignedShortType() ).create( img );

		final long m = Intervals.numElements( windowSize );
		LoopBuilder.setImages( sum, sumsqu, mean, variance ).multiThreaded().forEachPixel(
				( psum, psumsq, pmean, pvariance ) -> {
					final long lmean = psum.get() / m;
					final long lvariance = psumsq.get() / m - lmean * lmean;
					pmean.set( ( int ) lmean );
					pvariance.set( ( int ) lvariance );
				}
		);

		BdvFunctions.show( mean, "mean", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( variance, "variance", Bdv.options().addTo( bdv ) );
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
