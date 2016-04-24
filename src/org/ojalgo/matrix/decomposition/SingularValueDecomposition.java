/*
 * Copyright 1997-2015 Optimatika (www.optimatika.se)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.matrix.decomposition;

import org.ojalgo.access.Access2D;
import org.ojalgo.access.Structure2D;
import org.ojalgo.array.Array1D;
import org.ojalgo.constant.PrimitiveMath;
import org.ojalgo.function.BinaryFunction;
import org.ojalgo.matrix.store.ElementsSupplier;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.scalar.Scalar;

abstract class SingularValueDecomposition<N extends Number & Comparable<N>> extends GenericDecomposition<N> implements SingularValue<N> {

    private final BidiagonalDecomposition<N> myBidiagonal;
    private transient MatrixStore<N> myD;
    private boolean myFullSize = false;
    private transient MatrixStore<N> myInverse;
    private transient MatrixStore<N> myQ1;
    private transient MatrixStore<N> myQ2;
    private transient Array1D<Double> mySingularValues;
    private boolean mySingularValuesOnly = false;
    private boolean myTransposed = false;

    @SuppressWarnings("unused")
    private SingularValueDecomposition(final DecompositionStore.Factory<N, ? extends DecompositionStore<N>> aFactory) {
        this(aFactory, null);
    }

    protected SingularValueDecomposition(final DecompositionStore.Factory<N, ? extends DecompositionStore<N>> aFactory,
            final BidiagonalDecomposition<N> aBidiagonal) {

        super(aFactory);

        myBidiagonal = aBidiagonal;
    }

    public boolean computeValuesOnly(final ElementsSupplier<N> matrix) {
        return this.compute(matrix, true, false);
    }

    public boolean decompose(final ElementsSupplier<N> matrix) {
        return this.compute(matrix, false, this.isFullSize());
    }

    public double getCondition() {

        final Array1D<Double> tmpSingularValues = this.getSingularValues();

        return tmpSingularValues.doubleValue(0) / tmpSingularValues.doubleValue(tmpSingularValues.length - 1);
    }

    public MatrixStore<N> getD() {

        if ((myD == null) && this.isComputed()) {
            myD = this.makeD();
        }

        return myD;
    }

    public double getFrobeniusNorm() {

        double retVal = PrimitiveMath.ZERO;

        final Array1D<Double> tmpSingularValues = this.getSingularValues();
        double tmpVal;

        for (int i = tmpSingularValues.size() - 1; i >= 0; i--) {
            tmpVal = tmpSingularValues.doubleValue(i);
            retVal += tmpVal * tmpVal;
        }

        return Math.sqrt(retVal);
    }

    public MatrixStore<N> getInverse() {

        return this.getInverse(this.preallocate(new Structure2D() {

            public long countRows() {
                return myTransposed ? myBidiagonal.getColDim() : myBidiagonal.getRowDim();
            }

            public long countColumns() {
                return myTransposed ? myBidiagonal.getRowDim() : myBidiagonal.getColDim();
            }
        }));
    }

    public MatrixStore<N> getInverse(final DecompositionStore<N> preallocated) {

        if (myInverse == null) {

            final MatrixStore<N> tmpQ1 = this.getQ1();
            final Array1D<Double> tmpSingulars = this.getSingularValues();
            final MatrixStore<N> tmpQ2 = this.getQ2();

            final int tmpRowDim = (int) tmpSingulars.count();
            final int tmpColDim = (int) tmpQ1.countRows();
            final PhysicalStore<N> tmpMtrx = this.makeZero(tmpRowDim, tmpColDim);

            double tmpValue;
            final int rank = this.getRank();
            for (int i = 0; i < rank; i++) {
                tmpValue = tmpSingulars.doubleValue(i);
                for (int j = 0; j < tmpColDim; j++) {
                    tmpMtrx.set(i, j, tmpQ1.toScalar(j, i).conjugate().divide(tmpValue).getNumber());
                }
            }

            preallocated.fillByMultiplying(tmpQ2, tmpMtrx);
            myInverse = preallocated;
        }

        return myInverse;
    }

    private MatrixStore<N> getTruncatedInverse(final DecompositionStore<N> preallocated) {

        if (myInverse == null) {

            final MatrixStore<N> tmpQ1 = this.getQ1();
            final Array1D<Double> tmpSingulars = this.getSingularValues();
            final MatrixStore<N> tmpQ2 = this.getQ2();

            final PhysicalStore<N> tmpMtrx = tmpQ2.copy();

            final Scalar.Factory<N> tmpScalar = this.scalar();
            final BinaryFunction<N> tmpDivide = this.function().divide();
            final N tmpZero = tmpScalar.zero().getNumber();

            final int rank = this.getRank();
            for (int i = 0; i < rank; i++) {
                tmpMtrx.modifyColumn(0L, i, tmpDivide.second(tmpScalar.cast(tmpSingulars.doubleValue(i))));
            }
            final long tmpCountColumns = tmpMtrx.countColumns();
            for (int i = rank; i < tmpCountColumns; i++) {
                tmpMtrx.fillColumn(0L, i, tmpZero);
            }

            preallocated.fillByMultiplying(tmpMtrx, tmpQ1.conjugate());
            myInverse = preallocated;
        }

        return myInverse;
    }

    public double getKyFanNorm(final int k) {

        final Array1D<Double> tmpSingularValues = this.getSingularValues();

        double retVal = PrimitiveMath.ZERO;

        for (int i = Math.min(tmpSingularValues.size(), k) - 1; i >= 0; i--) {
            retVal += tmpSingularValues.doubleValue(i);
        }

        return retVal;
    }

    public double getOperatorNorm() {
        return this.getSingularValues().doubleValue(0);
    }

    public MatrixStore<N> getQ1() {

        if ((myQ1 == null) && !mySingularValuesOnly && this.isComputed()) {
            if (myTransposed) {
                myQ1 = this.makeQ2();
            } else {
                myQ1 = this.makeQ1();
            }
        }

        return myQ1;
    }

    public MatrixStore<N> getQ2() {

        if ((myQ2 == null) && !mySingularValuesOnly && this.isComputed()) {
            if (myTransposed) {
                myQ2 = this.makeQ1();
            } else {
                myQ2 = this.makeQ2();
            }
        }

        return myQ2;
    }

    public int getRank() {

        final Array1D<Double> tmpSingularValues = this.getSingularValues();
        int retVal = tmpSingularValues.size();

        // Tolerance based on min-dim but should be max-dim
        final double tmpTolerance = retVal * tmpSingularValues.doubleValue(0) * PrimitiveMath.MACHINE_EPSILON;

        for (int i = retVal - 1; i >= 0; i--) {
            if (tmpSingularValues.doubleValue(i) <= tmpTolerance) {
                retVal--;
            } else {
                return retVal;
            }
        }

        return retVal;
    }

    public Array1D<Double> getSingularValues() {

        if ((mySingularValues == null) && this.isComputed()) {
            mySingularValues = this.makeSingularValues();
        }

        return mySingularValues;
    }

    public double getTraceNorm() {
        return this.getKyFanNorm(this.getSingularValues().size());
    }

    public MatrixStore<N> invert(final Access2D<?> original) {
        this.decompose(this.wrap(original));
        return this.getInverse();
    }

    public MatrixStore<N> invert(final Access2D<?> original, final DecompositionStore<N> preallocated) {
        this.decompose(this.wrap(original));
        return this.getInverse(preallocated);
    }

    public boolean isFullSize() {
        return myFullSize;
    }

    public DecompositionStore<N> preallocate(final Structure2D template) {
        return this.allocate(template.countColumns(), template.countRows());
    }

    public DecompositionStore<N> preallocate(final Structure2D templateBody, final Structure2D templateRHS) {
        return this.allocate(templateRHS.countRows(), templateRHS.countColumns());
    }

    @Override
    public void reset() {

        super.reset();

        myBidiagonal.reset();

        myD = null;
        myQ1 = null;
        myQ2 = null;

        myInverse = null;

        mySingularValuesOnly = false;
        myTransposed = false;
    }

    public void setFullSize(final boolean fullSize) {
        myFullSize = fullSize;
    }

    public MatrixStore<N> solve(final Access2D<?> body, final Access2D<?> rhs) {
        this.decompose(this.wrap(body));
        return this.solve(this.wrap(rhs));
    }

    public MatrixStore<N> solve(final Access2D<?> body, final Access2D<?> rhs, final DecompositionStore<N> preallocated) {
        this.decompose(this.wrap(body));
        return this.solve(rhs, preallocated);
    }

    public final MatrixStore<N> solve(final ElementsSupplier<N> rhs) {
        return this.getInverse().multiply(rhs.get());
    }

    public MatrixStore<N> solve(final ElementsSupplier<N> rhs, final DecompositionStore<N> preallocated) {
        preallocated.fillByMultiplying(this.getInverse(), rhs.get());
        return preallocated;
    }

    protected boolean compute(final ElementsSupplier<N> matrix, final boolean singularValuesOnly, final boolean fullSize) {

        this.reset();

        if (matrix.countRows() >= matrix.countColumns()) {
            myTransposed = false;
        } else {
            myTransposed = true;
        }

        mySingularValuesOnly = singularValuesOnly;

        boolean retVal = false;

        try {

            retVal = this.doCompute(myTransposed ? matrix.get().conjugate() : matrix, singularValuesOnly, fullSize);

        } catch (final Exception anException) {

            BasicLogger.error(anException.toString());

            this.reset();

            retVal = false;
        }

        return this.computed(retVal);
    }

    protected boolean computeBidiagonal(final ElementsSupplier<N> matrix, final boolean fullSize) {
        myBidiagonal.setFullSize(fullSize);
        return myBidiagonal.decompose(matrix);
    }

    protected abstract boolean doCompute(ElementsSupplier<N> aMtrx, boolean singularValuesOnly, boolean fullSize);

    protected DiagonalAccess<N> getBidiagonalAccessD() {
        return myBidiagonal.getDiagonalAccessD();
    }

    protected DecompositionStore<N> getBidiagonalQ1() {
        return (DecompositionStore<N>) myBidiagonal.getQ1();
    }

    protected DecompositionStore<N> getBidiagonalQ2() {
        return (DecompositionStore<N>) myBidiagonal.getQ2();
    }

    protected boolean isTransposed() {
        return myTransposed;
    }

    protected abstract MatrixStore<N> makeD();

    protected abstract MatrixStore<N> makeQ1();

    protected abstract MatrixStore<N> makeQ2();

    protected abstract Array1D<Double> makeSingularValues();

    void setD(final MatrixStore<N> someD) {
        myD = someD;
    }

    void setQ1(final MatrixStore<N> someQ1) {
        myQ1 = someQ1;
    }

    void setQ2(final MatrixStore<N> someQ2) {
        myQ2 = someQ2;
    }

    void setSingularValues(final Array1D<Double> someSingularValues) {
        mySingularValues = someSingularValues;
    }

}
