package net.pietzsch.imglib;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.imglib2.Dimensions;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.linalg.eigen.TensorEigenValues;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.pietzsch.imglib.movingsum.SeparableMovingSum;

public class StructureTensorExample
{
	public static void main( String[] args ) throws ExecutionException, InterruptedException
	{
		ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/imglib/imglib2-introductory-workshop/target/classes/blobs.tif" );
//		ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/img1.tif" );
		final RandomAccessibleInterval< UnsignedByteType > img = copyToArrayImg( ImageJFunctions.wrapByte( imp ) );
		Bdv bdv = BdvFunctions.show( img, "img", Bdv.options().is2D() );

		final int blockSize = 21;
		final int n = img.numDimensions();

		final ArrayImgFactory< DoubleType > doubleImgFactory = new ArrayImgFactory<>( new DoubleType() );

		final RandomAccessibleInterval< DoubleType > gaussian = doubleImgFactory.create( img );

		final Dimensions gradientDims = Intervals.addDimension( img, 0, n - 1 );
		final RandomAccessibleInterval< DoubleType > gradient = doubleImgFactory.create( gradientDims );

		final Dimensions structureDims = Intervals.addDimension( img, 0, n * ( n + 1 ) / 2 - 1 );
		final RandomAccessibleInterval< DoubleType > structure = doubleImgFactory.create( structureDims );

		final int numthreads = Runtime.getRuntime().availableProcessors();
		final int nTasks = numthreads * 8;
		final ExecutorService es = Executors.newFixedThreadPool( numthreads );

		final double[] sigma = new double[] { 2, 2 };

		StructureTensor.calculateMatrix(
				Views.extendBorder( img ), // final RandomAccessible< T > source,
				gaussian, // final RandomAccessibleInterval< U > gaussian,
				gradient, // final RandomAccessibleInterval< U > gradient,
				structure, // final RandomAccessibleInterval< U > hessianMatrix,
				new OutOfBoundsBorderFactory<>(), // final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
				nTasks, es,
				sigma );

		BdvFunctions.show( gradient, "gradient", Bdv.options().addTo( bdv ) );
		BdvFunctions.show( structure, "structure", Bdv.options().addTo( bdv ) );

		final RandomAccessibleInterval< DoubleType > structureSum = doubleImgFactory.create( structureDims );

		for ( int i = 0; i < n * ( n + 1 ) / 2; ++i )
			SeparableMovingSum.convolve( new int[] { blockSize, blockSize },
					Views.extendBorder( Views.hyperSlice( structure, n, i ) ),
					Views.hyperSlice( structureSum, n, i ) );

		BdvFunctions.show( structureSum, "structureSum", Bdv.options().addTo( bdv ) );

//		--> now calculate eigenvalues

		ArrayImgFactory< DoubleType > imgFactory = new ArrayImgFactory<>( new DoubleType() );
		final RandomAccessibleInterval< DoubleType > eigenvalues = TensorEigenValues.calculateEigenValuesSymmetric(
				structureSum,
				TensorEigenValues.createAppropriateResultImg( structureSum, imgFactory ) );

		BdvFunctions.show( eigenvalues, "eigenvalues", Bdv.options().addTo( bdv ) );

		final List< Point > localExtrema = LocalExtrema.findLocalExtrema( Views.hyperSlice( eigenvalues, n, n - 1 ), new LocalExtrema.MaximumCheck<>( new DoubleType( 1000 ) ) );
		final ArrayImg< UnsignedByteType, ? > maxima = new ArrayImgFactory<>( new UnsignedByteType() ).create( img );
		final ArrayRandomAccess< UnsignedByteType > maximaRA = maxima.randomAccess();
		localExtrema.forEach( point -> {
			maximaRA.setPosition( point );
			maximaRA.get().set( 255 );
		} );

		BdvFunctions.show( maxima, "maxima", Bdv.options().addTo( bdv ) );
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > copyToArrayImg( final RandomAccessibleInterval< T > img )
	{
		final Img< T > copy = new ArrayImgFactory<>( Util.getTypeFromInterval( img ) ).create( img );
		LoopBuilder.setImages( img, copy ).forEachPixel( ( i, o ) -> o.set( i ) );
		return copy;
	}
}