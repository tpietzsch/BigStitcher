package net.pietzsch.old;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.CenteredRectangleShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.pietzsch.movingsum.SeparableMovingSum;

// Compare results of SeparableMovingSum and neighborhood-based reference implementation
public class BlockSumReference
{
	public static void main( String[] args )
	{
		ImagePlus imp = IJ.openImage("/Users/pietzsch/workspace/data/img1.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img = copyToArrayImg( ImageJFunctions.wrapByte( imp ) );
		Bdv bdv = BdvFunctions.show( img, "img", Bdv.options().is2D() );

		final RandomAccessibleInterval< UnsignedLongType > mean = new ArrayImgFactory<>( new UnsignedLongType() ).create( img );
		blockSumReference( Views.extendBorder( img ), mean, RealTypeConverters.getConverter( new UnsignedByteType(), new UnsignedLongType() ),11, 11 );

		BdvFunctions.show( mean, "sum", Bdv.options().addTo( bdv ) );

		final RandomAccessibleInterval< UnsignedLongType > mean2 = new ArrayImgFactory<>( new UnsignedLongType() ).create( img );
		SeparableMovingSum.convolve( new int[] { 11, 11 },
				Converters.convert(
						Views.extendBorder( img ),
						RealTypeConverters.getConverter( new UnsignedByteType(), new UnsignedLongType() ),
						new UnsignedLongType() ),
				mean2 );

		BdvFunctions.show( mean2, "sum", Bdv.options().addTo( bdv ) );
	}

	private static < T, O extends RealType< O > > void blockSumReference(
			final RandomAccessible< T > input,
			final RandomAccessibleInterval< O > output,
			final Converter< T, O > converter,
			final int ... blockSize )
	{
		final int[] span = new int[ blockSize.length ];
		for ( int d = 0; d < blockSize.length; ++d )
			span[ d ] = blockSize[ d ] / 2;
		final CenteredRectangleShape shape = new CenteredRectangleShape( span, false );

		O o = Util.getTypeFromInterval( output ).createVariable();

		final RandomAccess< Neighborhood< T > > na = shape.neighborhoodsRandomAccessible( input ).randomAccess();
		Cursor< O > c = Views.iterable( output ).localizingCursor();
		while ( c.hasNext() )
		{
			O out = c.next();
			na.setPosition( c );
			out.setZero();
			final Neighborhood< T > nh = na.get();
			for ( T t : nh )
			{
				converter.convert( t, o );
				out.add( o );
			}
		}
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
