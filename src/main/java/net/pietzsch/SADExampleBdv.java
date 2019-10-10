package net.pietzsch;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvSource;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class SADExampleBdv
{
	/*
	[+] open SpimData
	[+] open and show corresponding images
	[+] manual translation vector
	[+] downsample transforms, mask sizes, etc. into per-level struct
	[ ] SAD for downsampled levels
	 */

	public static void main( String[] args ) throws ExecutionException, InterruptedException, SpimDataException
	{
		final double[] manualTranslation = { 0.20676331652784175, -15.975192686499792, 142.8073272106439 }; // Tile3
		final int[] expectedTranslation0 = { 0, -16, 143 };
		final int[] blockSize0 = { 71, 71, 11 };
		final int[] searchSpan0 = { 16, 16, 16 };

		final SpimData2 spimdata = new XmlIoSpimData2( null ).load( "/Users/pietzsch/Desktop/David Chen/renamed/dataset.xml" );
		final ScaleSizes scales = new ScaleSizes( spimdata, blockSize0, searchSpan0, expectedTranslation0 );

		final int[] centerl0 = { 66, 302, 180 };

		final int level = 1;

		final RandomAccessibleInterval< UnsignedShortType > img1 = copyToArrayImg( scales.getImage( 0, level ) );
		final RandomAccessibleInterval< UnsignedShortType > img2 = copyToArrayImg( scales.getImage( 12, level ) );

		BdvSource sImg1 = BdvFunctions.show( img1, "img1" );
		Bdv bdv = sImg1;
		sImg1.setColor( new ARGBType( 0xff00ff ) );
		final AffineTransform3D img2Transform = new AffineTransform3D();
		img2Transform.translate( scales.scale( 0, level, manualTranslation ) );
		final BdvSource sImg2 = BdvFunctions.show( img2, "img2", Bdv.options().addTo( bdv ).sourceTransform( img2Transform ) );
		sImg2.setColor( new ARGBType( 0x00ff00 ) );




		// extract template
		final int n = scales.numDimensions();
		final int[] blockSize = scales.blockSize( level );
		final int[] searchSpan = scales.searchSpan( level );
		final int[] expectedTranslation = scales.expectedTranslation( level );

		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		Arrays.setAll( min, d -> - ( blockSize[ d ] - 1 ) / 2 );
		Arrays.setAll( max, d -> min[ d ] + blockSize[ d ] - 1 );
		final Interval templateInterval = new FinalInterval( min, max );

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] -= searchSpan[ d ];
			max[ d ] += searchSpan[ d ];
		}
		final Interval searchRegion = new FinalInterval( min, max );

		Arrays.setAll( min, d -> -searchSpan[ d ] );
		Arrays.setAll( max, d -> searchSpan[ d ] );
		final Interval sadInterval = new FinalInterval( min, max );

		final long[] center = Util.int2long( scales.scale( 0, level, centerl0, x -> ( int ) Math.round( x ) ) );
		System.out.println( "center = " + Arrays.toString( center ) );
		final IntervalView< UnsignedShortType > template = Views.interval( Views.translateInverse( Views.extendZero( img1 ), center ), templateInterval );

		final long[] centerImg2 =  new long[ n ];
		for ( int d = 0; d < n; ++d )
			centerImg2[ d ] = center[ d ] - expectedTranslation[ d ];
		final RandomAccessibleInterval< UnsignedShortType > region = Views.interval( Views.translateInverse( Views.extendZero( img2 ), centerImg2 ), searchRegion );

		BdvFunctions.show( template, "template", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( region, "region", Bdv.options().addTo( bdv ) );
	}


	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
