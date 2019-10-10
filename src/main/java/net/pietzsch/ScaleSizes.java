package net.pietzsch;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import java.util.Arrays;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class ScaleSizes
{
	private final AbstractSpimData< ? > spimdata;

	private final ViewerImgLoader imgLoader;

	private final int[][] blockSizes;

	private final int[][] searchSpans;

	private final int[][] expectedTranslations;

	private final double[][] downsamplingFactors;

	private final int n;

	private final int nlevels;

	public ScaleSizes( final AbstractSpimData< ? > spimdata, final int[] blockSize, final int[] searchSpan, final int[] expectedTranslation )
	{
		this.spimdata = spimdata;
		imgLoader = ( ViewerImgLoader ) spimdata.getSequenceDescription().getImgLoader();
		final ViewerSetupImgLoader< UnsignedShortType, ? > sil = ( ViewerSetupImgLoader< UnsignedShortType, ? > ) imgLoader.getSetupImgLoader( 0 );
		downsamplingFactors = sil.getMipmapResolutions();

		n = downsamplingFactors[ 0 ].length;
		nlevels = downsamplingFactors.length;

		blockSizes = new int[ nlevels ][ n ];
		searchSpans = new int[ nlevels ][ n ];
		expectedTranslations = new int[ nlevels ][ n ];

		for ( int level = 0; level < nlevels; ++level )
		{
			scale( 0, level, blockSize, blockSizes[ level ], x1 -> {
				final int c = ( int ) Math.ceil( x1 );
				return c % 2 == 0 ? c + 1 : c;
			} );
			scale( 0, level, searchSpan, searchSpans[ level ], x -> ( int ) Math.ceil( x ) );
			scale( 0, level, expectedTranslation, expectedTranslations[ level ], x -> ( int ) Math.round( x ) );
		}
	}

	public int numDimensions()
	{
		return n;
	}

	public int[] blockSize( final int level )
	{
		return blockSizes[ level ];
	}

	public int[] searchSpan( final int level )
	{
		return searchSpans[ level ];
	}

	public int[] expectedTranslation( final int level )
	{
		return expectedTranslations[ level ];
	}

	public RandomAccessibleInterval< UnsignedShortType > getImage( final int setupId, final int level )
	{
		return ( ( ViewerSetupImgLoader< UnsignedShortType, ? > ) imgLoader.getSetupImgLoader( setupId ) ).getImage( 0, level );
	}

	// scaling

	public double[] scale( final int fromLevel, final int toLevel, final double[] v )
	{
		final double[] sv = new double[ v.length ];
		return scale( fromLevel, toLevel, v, sv );
	}

	public double[] scale( final int fromLevel, final int toLevel, final double[] v, final double[] sv )
	{
		// l_to = l_from * f_from / f_to
		Arrays.setAll( sv, d -> v[ d ] * downsamplingFactors[ fromLevel ][ d ] / downsamplingFactors[ toLevel ][ d ] );
		return sv;
	}

	public long[] scale( final int fromLevel, final int toLevel, final long[] v )
	{
		return scale( fromLevel, toLevel, v, x -> ( long ) Math.ceil( x ) );
	}

	public long[] scale( final int fromLevel, final int toLevel, final long[] v, final DoubleToLongFunction round )
	{
		final long[] sv = new long[ v.length ];
		return scale( fromLevel, toLevel, v, sv, round );
	}

	public long[] scale( final int fromLevel, final int toLevel, final long[] v, final long[] sv, final DoubleToLongFunction round )
	{
		// l_to = l_from * f_from / f_to
		Arrays.setAll( sv, d -> round.applyAsLong(v[ d ] * downsamplingFactors[ fromLevel ][ d ] / downsamplingFactors[ toLevel ][ d ] ) );
		return sv;
	}

	public int[] scale( final int fromLevel, final int toLevel, final int[] v )
	{
		return scale( fromLevel, toLevel, v, x -> ( int ) Math.ceil( x ) );
	}

	public int[] scale( final int fromLevel, final int toLevel, final int[] v, final DoubleToIntFunction round )
	{
		final int[] sv = new int[ v.length ];
		return scale( fromLevel, toLevel, v, sv, round );
	}

	public int[] scale( final int fromLevel, final int toLevel, final int[] v, final int[] sv, final DoubleToIntFunction round )
	{
		// l_to = l_from * f_from / f_to
		Arrays.setAll( sv, d -> round.applyAsInt(v[ d ] * downsamplingFactors[ fromLevel ][ d ] / downsamplingFactors[ toLevel ][ d ] ) );
		return sv;
	}
}
