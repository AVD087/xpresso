/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wantedtech.common.xpresso.helpers;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import com.wantedtech.common.xpresso.x;
import com.wantedtech.common.xpresso.types.tuple.tuple2;


public class Equals {

    /**
     * <p>
     * A registry of objects used by reflection methods to detect cyclical object references and avoid infinite loops.
     * </p>
     *
     * @since 3.0
     */
    private final ThreadLocal<Set<tuple2<IDKey, IDKey>>> REGISTRY = new ThreadLocal<Set<tuple2<IDKey, IDKey>>>();

    /*
     * NOTE: we cannot store the actual objects in a HashSet, as that would use the very hashCode()
     * we are in the process of calculating.
     *
     * So we generate a one-to-one mapping from the original object to a new object.
     *
     * Now HashSet uses equals() to determine if two elements with the same hashcode really
     * are equal, so we also need to ensure that the replacement objects are only equal
     * if the original objects are identical.
     *
     * The original implementation (2.4 and before) used the System.indentityHashCode()
     * method - however this is not guaranteed to generate unique ids (e.g. LANG-459)
     *
     * We now use the IDKey helper class (adapted from org.apache.axis.utils.IDKey)
     * to disambiguate the duplicate ids.
     */

    /**
     * <p>
     * Returns the registry of object pairs being traversed by the reflection
     * methods in the current thread.
     * </p>
     *
     * @return Set the registry of objects being traversed
     * @since 3.0
     */
    Set<tuple2<IDKey, IDKey>> getRegistry() {
        return REGISTRY.get();
    }

    /**
     * <p>
     * Converters value pair into a register pair.
     * </p>
     *
     * @param lhs <code>this</code> object
     * @param rhs the other object
     *
     * @return the pair
     */
    static tuple2<IDKey, IDKey> getRegisterPair(final Object lhs, final Object rhs) {
        final IDKey left = new IDKey(lhs);
        final IDKey right = new IDKey(rhs);
        return tuple2.valueOf(left, right);
    }

    /**
     * <p>
     * Returns <code>true</code> if the registry contains the given object pair.
     * Used by the reflection methods to avoid infinite loops.
     * Objects might be swapped therefore a check is needed if the object pair
     * is registered in given or swapped order.
     * </p>
     *
     * @param lhs <code>this</code> object to lookup in registry
     * @param rhs the other object to lookup on registry
     * @return boolean <code>true</code> if the registry contains the given object.
     * @since 3.0
     */
    boolean isRegistered(final Object lhs, final Object rhs) {
        final Set<tuple2<IDKey, IDKey>> registry = getRegistry();
        final tuple2<IDKey, IDKey> pair = getRegisterPair(lhs, rhs);
        final tuple2<IDKey, IDKey> swappedPair = tuple2.valueOf(pair.value0, pair.value1);

        return registry != null
                && (registry.contains(pair) || registry.contains(swappedPair));
    }

    /**
     * <p>
     * Registers the given object pair.
     * Used by the reflection methods to avoid infinite loops.
     * </p>
     *
     * @param lhs <code>this</code> object to register
     * @param rhs the other object to register
     */
    void register(final Object lhs, final Object rhs) {
        synchronized (Equals.class) {
            if (getRegistry() == null) {
                REGISTRY.set(new HashSet<tuple2<IDKey, IDKey>>());
            }
        }

        final Set<tuple2<IDKey, IDKey>> registry = getRegistry();
        final tuple2<IDKey, IDKey> pair = getRegisterPair(lhs, rhs);
        registry.add(pair);
    }

    /**
     * <p>
     * Unregisters the given object pair.
     * </p>
     *
     * <p>
     * Used by the reflection methods to avoid infinite loops.
     *
     * @param lhs <code>this</code> object to unregister
     * @param rhs the other object to unregister
     * @since 3.0
     */
    void unregister(final Object lhs, final Object rhs) {
        Set<tuple2<IDKey, IDKey>> registry = getRegistry();
        if (registry != null) {
            final tuple2<IDKey, IDKey> pair = getRegisterPair(lhs, rhs);
            registry.remove(pair);
            synchronized (Equals.class) {
                //read again
                registry = getRegistry();
                if (registry != null && registry.isEmpty()) {
                    REGISTRY.remove();
                }
            }
        }
    }

    /**
     * If the fields tested are equals.
     * The default value is <code>true</code>.
     */
    private boolean isEquals = true;
    
    private Object lhs;
    private Object rhs;
    private String[] excludeFields;

    /**
     * <p>Constructor for Equals.</p>
     * 
     * @param lhs  <code>this</code> object
     * @param excludeFields  array of field names to exclude from testing

     * <p>Starts off assuming that equals is <code>true</code>.</p>
     * @see Object#equals(Object)
     */
    public Equals(Object lhs, Object rhs, String... excludeFields) {
    	this.lhs = lhs;
    	this.rhs = rhs;
    	this.excludeFields = excludeFields;
    	computeEquals();
    }

    //-------------------------------------------------------------------------

    /**
     * <p>This method uses reflection to determine if the two <code>Object</code>s
     * are equal.</p>
     *
     * <p>It uses <code>AccessibleObject.setAccessible</code> to gain access to private
     * fields. This means that it will throw a security exception if run under
     * a security manager, if the permissions are not set up correctly. It is also
     * not as efficient as testing explicitly. Non-primitive fields are compared using 
     * <code>equals()</code>.</p>
     *
     * <p>If the testTransients parameter is set to <code>true</code>, transient
     * members will be tested, otherwise they are ignored, as they are likely
     * derived fields, and not part of the value of the <code>Object</code>.</p>
     *
     * <p>Static fields will not be included. Superclass fields will be appended
     * up to and including the specified superclass. A null superclass is treated
     * as java.lang.Object.</p>
     *
     * @param rhs  the other object
     * @return <code>true</code> if the two Objects have tested equals.
     * 
     * @see EqualsExclude
     * @since 2.0
     */
    public void computeEquals() {
        if (this.lhs == this.rhs) {
        	this.isEquals = true;
        	return;
        }
        if (this.lhs == null || this.rhs == null) {
        	this.isEquals = false;
        	return;
        }
        // Find the leaf class since there may be transients in the leaf
        // class or in classes between the leaf and root.
        // If we are not testing transients or a subclass has no ivars,
        // then a subclass can test equals to a superclass.
        final Class<?> lhsClass = this.lhs.getClass();
        final Class<?> rhsClass = this.rhs.getClass();
        Class<?> testClass;
        if (lhsClass.isInstance(this.rhs)) {
            testClass = lhsClass;
            if (!rhsClass.isInstance(this.lhs)) {
                // rhsClass is a subclass of lhsClass
                testClass = rhsClass;
            }
        } else if (rhsClass.isInstance(this.lhs)) {
            testClass = rhsClass;
            if (!lhsClass.isInstance(this.rhs)) {
                // lhsClass is a subclass of rhsClass
                testClass = lhsClass;
            }
        } else {
            // The two classes are not related.
        	this.isEquals = false;
        	return;
        }
        try {
            if (testClass.isArray()) {
                this.append(this.lhs, this.rhs);
            } else {
                reflectionAppend(this.lhs, this.rhs, testClass, excludeFields);
                while (testClass.getSuperclass() != null) {
                    testClass = testClass.getSuperclass();
                    reflectionAppend(this.lhs, this.rhs, testClass, excludeFields);
                }
            }
        } catch (final IllegalArgumentException e) {
            // In this case, we tried to test a subclass vs. a superclass and
            // the subclass has ivars or the ivars are transient and
            // we are testing transients.
            // If a subclass has ivars that we are trying to test them, we get an
            // exception and we know that the objects are not equal.
        	this.isEquals = false;
            return;
        }
    }

    /**
     * <p>Appends the fields and values defined by the given object of the
     * given Class.</p>
     *
     * @param lhs  the left hand object
     * @param rhs  the right hand object
     * @param clazz  the class to append details of
     * @param builder  the builder to append to
     * @param useTransients  whether to test transient fields
     * @param excludeFields  array of field names to exclude from testing
     */
    private void reflectionAppend(
        final Object lhs,
        final Object rhs,
        final Class<?> clazz,
        final String[] excludeFields) {

        if (isRegistered(lhs, rhs)) {
            return;
        }

        try {
            register(lhs, rhs);
            final Field[] fields = clazz.getDeclaredFields();
            AccessibleObject.setAccessible(fields, true);
            for (int i = 0; i < fields.length && this.isEquals; i++) {
                final Field f = fields[i];
                if (!x.list(excludeFields).contains(f.getName())
                    && !f.getName().contains("$")
                    && (!Modifier.isTransient(f.getModifiers()))
                    && (!Modifier.isStatic(f.getModifiers()))
                    && (!f.isAnnotationPresent(EqualsExclude.class))) {
                    try {
                        this.append(f.get(lhs), f.get(rhs));
                    } catch (final IllegalAccessException e) {
                        //this can't happen. Would get a Security exception instead
                        //throw a runtime exception in case the impossible happens.
                        throw new InternalError("Unexpected IllegalAccessException");
                    }
                }
            }
        } finally {
            unregister(lhs, rhs);
        }
    }

    //-------------------------------------------------------------------------

    /**
     * <p>Test if two <code>Object</code>s are equal using their
     * <code>equals</code> method.</p>
     *
     * @param lhs  the left hand object
     * @param rhs  the right hand object
     * @return Equals - used to chain calls.
     */
    public Equals append(final Object lhs, final Object rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        final Class<?> lhsClass = lhs.getClass();
        if (!lhsClass.isArray()) {
            // The simple case, not an array, just test the element
            isEquals = lhs.equals(rhs);
        } else if (lhs.getClass() != rhs.getClass()) {
            // Here when we compare different dimensions, for example: a boolean[][] to a boolean[]
            this.setEquals(false);
        }
        // 'Switch' on type of array, to dispatch to the correct handler
        // This handles multi dimensional arrays of the same depth
        else if (lhs instanceof long[]) {
            append((long[]) lhs, (long[]) rhs);
        } else if (lhs instanceof int[]) {
            append((int[]) lhs, (int[]) rhs);
        } else if (lhs instanceof short[]) {
            append((short[]) lhs, (short[]) rhs);
        } else if (lhs instanceof char[]) {
            append((char[]) lhs, (char[]) rhs);
        } else if (lhs instanceof byte[]) {
            append((byte[]) lhs, (byte[]) rhs);
        } else if (lhs instanceof double[]) {
            append((double[]) lhs, (double[]) rhs);
        } else if (lhs instanceof float[]) {
            append((float[]) lhs, (float[]) rhs);
        } else if (lhs instanceof boolean[]) {
            append((boolean[]) lhs, (boolean[]) rhs);
        } else {
            // Not an array of primitives
            append((Object[]) lhs, (Object[]) rhs);
        }
        return this;
    }

    /**
     * <p>
     * Test if two <code>long</code> s are equal.
     * </p>
     *
     * @param lhs
     *                  the left hand <code>long</code>
     * @param rhs
     *                  the right hand <code>long</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final long lhs, final long rhs) {
        if (!isEquals) {
            return this;
        }
        isEquals = lhs == rhs;
        return this;
    }

    /**
     * <p>Test if two <code>int</code>s are equal.</p>
     *
     * @param lhs  the left hand <code>int</code>
     * @param rhs  the right hand <code>int</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final int lhs, final int rhs) {
        if (!isEquals) {
            return this;
        }
        isEquals = lhs == rhs;
        return this;
    }

    /**
     * <p>Test if two <code>short</code>s are equal.</p>
     *
     * @param lhs  the left hand <code>short</code>
     * @param rhs  the right hand <code>short</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final short lhs, final short rhs) {
        if (!isEquals) {
            return this;
        }
        isEquals = lhs == rhs;
        return this;
    }

    /**
     * <p>Test if two <code>char</code>s are equal.</p>
     *
     * @param lhs  the left hand <code>char</code>
     * @param rhs  the right hand <code>char</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final char lhs, final char rhs) {
        if (!isEquals) {
            return this;
        }
        isEquals = lhs == rhs;
        return this;
    }

    /**
     * <p>Test if two <code>byte</code>s are equal.</p>
     *
     * @param lhs  the left hand <code>byte</code>
     * @param rhs  the right hand <code>byte</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final byte lhs, final byte rhs) {
        if (!isEquals) {
            return this;
        }
        isEquals = lhs == rhs;
        return this;
    }

    /**
     * <p>Test if two <code>double</code>s are equal by testing that the
     * pattern of bits returned by <code>doubleToLong</code> are equal.</p>
     *
     * <p>This handles NaNs, Infinities, and <code>-0.0</code>.</p>
     *
     * <p>It is compatible with the hash code generated by
     * <code>HashCodeBuilder</code>.</p>
     *
     * @param lhs  the left hand <code>double</code>
     * @param rhs  the right hand <code>double</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final double lhs, final double rhs) {
        if (!isEquals) {
            return this;
        }
        return append(Double.doubleToLongBits(lhs), Double.doubleToLongBits(rhs));
    }

    /**
     * <p>Test if two <code>float</code>s are equal byt testing that the
     * pattern of bits returned by doubleToLong are equal.</p>
     *
     * <p>This handles NaNs, Infinities, and <code>-0.0</code>.</p>
     *
     * <p>It is compatible with the hash code generated by
     * <code>HashCodeBuilder</code>.</p>
     *
     * @param lhs  the left hand <code>float</code>
     * @param rhs  the right hand <code>float</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final float lhs, final float rhs) {
        if (!isEquals) {
            return this;
        }
        return append(Float.floatToIntBits(lhs), Float.floatToIntBits(rhs));
    }

    /**
     * <p>Test if two <code>booleans</code>s are equal.</p>
     *
     * @param lhs  the left hand <code>boolean</code>
     * @param rhs  the right hand <code>boolean</code>
     * @return Equals - used to chain calls.
      */
    public Equals append(final boolean lhs, final boolean rhs) {
        if (!isEquals) {
            return this;
        }
        isEquals = lhs == rhs;
        return this;
    }

    /**
     * <p>Performs a deep comparison of two <code>Object</code> arrays.</p>
     *
     * <p>This also will be called for the top level of
     * multi-dimensional, ragged, and multi-typed arrays.</p>
     *
     * @param lhs  the left hand <code>Object[]</code>
     * @param rhs  the right hand <code>Object[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final Object[] lhs, final Object[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>long</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(long, long)} is used.</p>
     *
     * @param lhs  the left hand <code>long[]</code>
     * @param rhs  the right hand <code>long[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final long[] lhs, final long[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>int</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(int, int)} is used.</p>
     *
     * @param lhs  the left hand <code>int[]</code>
     * @param rhs  the right hand <code>int[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final int[] lhs, final int[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>short</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(short, short)} is used.</p>
     *
     * @param lhs  the left hand <code>short[]</code>
     * @param rhs  the right hand <code>short[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final short[] lhs, final short[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>char</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(char, char)} is used.</p>
     *
     * @param lhs  the left hand <code>char[]</code>
     * @param rhs  the right hand <code>char[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final char[] lhs, final char[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>byte</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(byte, byte)} is used.</p>
     *
     * @param lhs  the left hand <code>byte[]</code>
     * @param rhs  the right hand <code>byte[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final byte[] lhs, final byte[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>double</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(double, double)} is used.</p>
     *
     * @param lhs  the left hand <code>double[]</code>
     * @param rhs  the right hand <code>double[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final double[] lhs, final double[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>float</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(float, float)} is used.</p>
     *
     * @param lhs  the left hand <code>float[]</code>
     * @param rhs  the right hand <code>float[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final float[] lhs, final float[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Deep comparison of array of <code>boolean</code>. Length and all
     * values are compared.</p>
     *
     * <p>The method {@link #append(boolean, boolean)} is used.</p>
     *
     * @param lhs  the left hand <code>boolean[]</code>
     * @param rhs  the right hand <code>boolean[]</code>
     * @return Equals - used to chain calls.
     */
    public Equals append(final boolean[] lhs, final boolean[] rhs) {
        if (!isEquals) {
            return this;
        }
        if (lhs == rhs) {
            return this;
        }
        if (lhs == null || rhs == null) {
            this.setEquals(false);
            return this;
        }
        if (lhs.length != rhs.length) {
            this.setEquals(false);
            return this;
        }
        for (int i = 0; i < lhs.length && isEquals; ++i) {
            append(lhs[i], rhs[i]);
        }
        return this;
    }

    /**
     * <p>Returns <code>true</code> if the fields that have been checked
     * are all equal.</p>
     *
     * @return boolean
     */
    public boolean getEquals() {
        return this.isEquals;
    }

    /**
     * Sets the <code>isEquals</code> value.
     *
     * @param isEquals The value to set.
     * @since 2.1
     */
    protected void setEquals(final boolean isEquals) {
        this.isEquals = isEquals;
    }

}
