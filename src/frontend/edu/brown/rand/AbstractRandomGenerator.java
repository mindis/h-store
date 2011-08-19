/***************************************************************************
 *   Copyright (C) 2009 by H-Store Project                                 *
 *   Brown University                                                      *
 *   Massachusetts Institute of Technology                                 *
 *   Yale University                                                       *
 *                                                                         *
 *   Permission is hereby granted, free of charge, to any person obtaining *
 *   a copy of this software and associated documentation files (the       *
 *   "Software"), to deal in the Software without restriction, including   *
 *   without limitation the rights to use, copy, modify, merge, publish,   *
 *   distribute, sublicense, and/or sell copies of the Software, and to    *
 *   permit persons to whom the Software is furnished to do so, subject to *
 *   the following conditions:                                             *
 *                                                                         *
 *   The above copyright notice and this permission notice shall be        *
 *   included in all copies or substantial portions of the Software.       *
 *                                                                         *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,       *
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF    *
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.*
 *   IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR     *
 *   OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, *
 *   ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR *
 *   OTHER DEALINGS IN THE SOFTWARE.                                       *
 ***************************************************************************/
package edu.brown.rand;

import java.lang.reflect.*;
import java.util.*;

public abstract class AbstractRandomGenerator extends Random {
    private static final long serialVersionUID = 1L;

    private final static Map<String, Constructor<? extends AbstractRandomGenerator>> factoryCache = new HashMap<String, Constructor<? extends AbstractRandomGenerator>>();
    
    /**
     * 
     * @param seed
     */
    public AbstractRandomGenerator() {
        super();
    }
    
    /**
     * 
     * @param seed
     */
    public AbstractRandomGenerator(Integer seed) {
        super(seed);
    }

    /**
     * 
     * @param minimum
     * @param maximum
     * @returns a int in the range [minimum, maximum]. Note that this is inclusive.
     */
    public int number(int minimum, int maximum) {
        assert minimum <= maximum;
        int range_size = maximum - minimum + 1;
        int value = this.nextInt(range_size);
        value += minimum;
        assert minimum <= value && value <= maximum;
        return value;
    }
    
    public long number(long minimum, long maximum) {
        long range_size = (maximum - minimum) + 1;
        
        // error checking and 2^x checking removed for simplicity.
        long bits, val;
        do {
            bits = (this.nextLong() << 1) >>> 1;
            val = bits % range_size;
        } while (bits - val + range_size < 0L);
        val += minimum;
        assert(val >= minimum);
        assert(val <= maximum);
        return val;
    }

    
    /**
     * 
     * @param minimum
     * @param maximum
     * @param excluding
     * @returns an int in the range [minimum, maximum], excluding excluding.
     */
    public int numberExcluding(int minimum, int maximum, int excluding) {
        assert minimum < maximum;
        assert minimum <= excluding && excluding <= maximum;

        // Generate 1 less number than the range
        int num = number(minimum, maximum-1);

        // Adjust the numbers to remove excluding
        if (num >= excluding) {
            num += 1;
        }
        assert minimum <= num && num <= maximum && num != excluding;
        return num;
    }
    
    /**
     * 
     * @param minimum
     * @param maximum
     * @param excluding
     * @return
     */
    public abstract int numberExcluding(int minimum, int maximum, int excluding, String source_table, String target_table);

    
    /**
     * Returns a random int in a skewed gaussian distribution of the range
     * Note that the range is inclusive
     * A skew factor of 0.0 means that it's a uniform distribution
     * The greater the skew factor the higher the probability the selected random
     * value will be closer to the mean of the range
     *  
     * @param minimum the minimum random number
     * @param maximum the maximum random number
     * @param skewFactor the factor to skew the stddev of the gaussian distribution
     */
    public int numberSkewed(int minimum, int maximum, double skewFactor) {
        // Calling number() when the skewFactor is zero will likely be faster
        // than using our Gaussian distribution method below 
        if (skewFactor == 0) return (this.number(minimum, maximum));

        assert minimum <= maximum;
        int range_size = maximum - minimum + 1;
        int mean = range_size / 2;
        double stddev = range_size - ((range_size / 1.1) * skewFactor);
        int value = -1;
        while (value < 0 || value >= range_size) {
            value = (int) Math.round(this.nextGaussian() * stddev) + mean;
        }
        value += minimum;
        assert minimum <= value && value <= maximum;
        return value;
    }

    /**
     * Returns a random int based on some affinity distribution for the provided base number.
     * For example, if the base is '5', then based on the distribution implemented in the class
     * the value '5' might have a higher probability of returning the value '6'.   
     * @param minimum the minimum random number
     * @param maximum the maximum random number
     * @param base the base number to use when selecting a random number
     * @param table the name of the table this value is being generated for
     */
    public abstract int numberAffinity(int minimum, int maximum, int base, String base_table, String target_table);

    /**
     * Load a profile from the given input path into the Generator
     * @param input_path
     * @throws Exception
     */
    public abstract void loadProfile(String input_path) throws Exception;

    /**
     * Save the generator's current profile to file at output path.
     * @param output_path
     * @throws Exception
     */
    public abstract void saveProfile(String output_path) throws Exception;
    
    /**
     * 
     * @param decimal_places
     * @param minimum
     * @param maximum
     * @return
     */
    public double fixedPoint(int decimal_places, double minimum, double maximum) {
        assert decimal_places > 0;
        assert minimum < maximum : String.format("%f < %f", minimum, maximum);

        int multiplier = 1;
        for (int i = 0; i < decimal_places; ++i) {
            multiplier *= 10;
        }

        int int_min = (int)(minimum * multiplier + 0.5);
        int int_max = (int)(maximum * multiplier + 0.5);
        
        return (double) number(int_min, int_max) / (double) multiplier;
    }
    
    /** @returns a random alphabetic string with length in range [minimum_length, maximum_length].
     */
    public String astring(int minimum_length, int maximum_length) {
        return randomString(minimum_length, maximum_length, 'a', 26);
    }


    /** @returns a random numeric string with length in range [minimum_length, maximum_length].
     */
    public String nstring(int minimum_length, int maximum_length) {
        return randomString(minimum_length, maximum_length, '0', 10);
    }

    /**
     * 
     * @param minimum_length
     * @param maximum_length
     * @param base
     * @param numCharacters
     * @return
     */
    private String randomString(int minimum_length, int maximum_length, char base, int numCharacters) {
        int length = number(minimum_length, maximum_length);
        byte baseByte = (byte) base;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; ++i) {
            bytes[i] = (byte)(baseByte + number(0, numCharacters-1));
        }
        return new String(bytes);
    }
    
    /**
     * 
     * @param className
     * @param seed
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static AbstractRandomGenerator factory(String className, int seed) throws RuntimeException {
        //
        // Create constructor handle for RandomGenerator
        //
        if (!factoryCache.containsKey(className)) {
            Constructor<? extends AbstractRandomGenerator> randGenConstructor = null;
            try {
                ClassLoader loader = ClassLoader.getSystemClassLoader();
                Class<? extends AbstractRandomGenerator> randGenClass = (Class<? extends AbstractRandomGenerator>)loader.loadClass(className);
                randGenConstructor = randGenClass.getConstructor(new Class[]{ Integer.class }); 
            } catch (Exception ex) {
                System.err.println("Failed to retrieve constructor for " + className);
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }
            factoryCache.put(className, randGenConstructor);
        }
        AbstractRandomGenerator rng = null;
        try {
            rng = factoryCache.get(className).newInstance(new Object[]{ Integer.valueOf(seed) });
        } catch (Exception ex) {
            System.err.println("Failed to instantiate object for " + className);
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        return (rng);
    }
}