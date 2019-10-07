package net.pietzsch.old;

import bdv.util.AxisOrder;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import java.util.Arrays;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;

// Compare results of SeparableMovingSum and neighborhood-based reference implementation
public class SumOfGradients
{
	public static void main( String[] args )
	{
		ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/imglib/imglib2-introductory-workshop/target/classes/blobs.tif" );
//		ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/img1.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img = copyToArrayImg( ImageJFunctions.wrapByte( imp ) );
		Bdv bdv = BdvFunctions.show( img, "img", Bdv.options().is2D() );

		final int blockSize = 21;
		final int n = img.numDimensions();

		// Image of n+1 dimensions to store the partial derivatives of the input
		// image. The (n+1)-th dimension is used to index the partial
		// derivative. For example, the partial derivative by Y of pixel (a,b,c)
		// is stored at position (a,b,c,1).
		final long[] dim = new long[ n + 1 ];
		for ( int d = 0; d < n; ++d )
			dim[ d ] = img.dimension( d );
		dim[ n ] = n;
		final Img< LongType > partials = new ArrayImgFactory<>( new LongType() ).create( dim );

		// Compute partial derivatives of input in all dimension. This requires
		// a border of 1 pixel with respect to the input image
		for ( int d = 0; d < n; ++d )
			PartialDerivative.gradientCentralDifference(
					Converters.convert(
							Views.extendBorder( img ),
							RealTypeConverters.getConverter( new UnsignedByteType(), new LongType() ),
							new LongType() ),
					Views.hyperSlice( partials, n, d ),
					d );

		BdvFunctions.show( partials, "gradients", Bdv.options().addTo( bdv ).axisOrder( AxisOrder.XYC ) );

		final RandomAccessibleInterval< UnsignedLongType > gradmag = new ArrayImgFactory<>( new UnsignedLongType() ).create( img );

		LoopBuilder.setImages( Views.collapseReal( partials ), gradmag ).forEachPixel( ( ps, g ) -> {
			long sumsq = 0;
			for ( int d = 0; d < n; ++d )
			{
				long p = ps.get( d ).get();
				sumsq += p * p;
			}
			g.set( ( long ) Math.sqrt( sumsq ) );
		} );

		System.out.println( "gradmag = " + gradmag );
		BdvFunctions.show( gradmag, "gradient magnitude", Bdv.options().addTo( bdv ) );

//		Views.interval( img, Intervals.createMinSize( img.dimension( 0 ) / 2, img.dimension( 1 ) / 2, blockSize, blockSize ) ).forEach( t -> t.set( 0 ) );

	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
