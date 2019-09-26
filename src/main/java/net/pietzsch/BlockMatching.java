package net.pietzsch;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.phasecorrelation.PhaseCorrelation2;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class BlockMatching
{
	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ImagePlus imp = IJ.openImage("/Users/pietzsch/workspace/data/img1.tif" );

		final RandomAccessibleInterval< UnsignedByteType > img = copyToArrayImg( ImageJFunctions.wrapByte( imp ) );
		final RandomAccessibleInterval< UnsignedByteType > template = copyToArrayImg( Views.zeroMin( Views.interval( img, Intervals.createMinSize( 140, 70, 50, 50 ) ) ) );

		Bdv bdv = BdvFunctions.show( img, "img", Bdv.options().is2D() );
		BdvFunctions.show( template, "template", Bdv.options().addTo( bdv ) );

		final int[] extension = { 10, 10 };
		final ExecutorService service = Executors.newFixedThreadPool( Math.max( 2, Runtime.getRuntime().availableProcessors() ) );
		final RandomAccessibleInterval< FloatType > pcm = PhaseCorrelation2.calculatePCM(
				template, img,
				extension, new ArrayImgFactory<>( new FloatType() ), new FloatType(),
				new ArrayImgFactory<>( new ComplexFloatType() ), new ComplexFloatType(),
				service );

		service.shutdown();

		BdvFunctions.show( pcm, "pcm", Bdv.options().addTo( bdv ) );
	}

	private static RandomAccessibleInterval< UnsignedByteType> copyToArrayImg( final RandomAccessibleInterval< UnsignedByteType > img )
	{
		final Img< UnsignedByteType > copy = new ArrayImgFactory<>( new UnsignedByteType() ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}
