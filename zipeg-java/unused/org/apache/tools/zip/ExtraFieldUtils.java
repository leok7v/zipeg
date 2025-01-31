/*
 * Copyright  2001-2002,2004-2005 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.tools.zip;

import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipException;

/**
 * ZipExtraField related methods
 *
 */
public class ExtraFieldUtils {

    /**
     * Static registry of known extra fields.
     *
     * @since 1.1
     */
    private static Hashtable implementations;

    static {
        implementations = new Hashtable();
        register(AsiExtraField.class);
        register(JarMarker.class);
    }

    /**
     * Register a ZipExtraField implementation.
     *
     * <p>The given class must have a no-arg constructor and implement
     * the {@link ZipExtraField ZipExtraField interface}.</p>
     *
     * @since 1.1
     */
    public static void register(Class c) {
        try {
            ZipExtraField ze = (ZipExtraField) c.newInstance();
            implementations.put(ze.getHeaderId(), c);
        } catch (ClassCastException cc) {
            throw new RuntimeException(c + " doesn\'t implement ZipExtraField");
        } catch (InstantiationException ie) {
            throw new RuntimeException(c + " is not a concrete class");
        } catch (IllegalAccessException ie) {
            throw new RuntimeException(c + "\'s no-arg constructor is not public");
        }
    }

    /*
     * Create an instance of the approriate ExtraField, falls back to
     * {@link UnrecognizedExtraField UnrecognizedExtraField}.
     *
     * @since 1.1
     */
    public static ZipExtraField createExtraField(ZipShort headerId)
        throws InstantiationException, IllegalAccessException {
        Class c = (Class) implementations.get(headerId);
        if (c != null) {
            return (ZipExtraField) c.newInstance();
        }
        UnrecognizedExtraField u = new UnrecognizedExtraField();
        u.setHeaderId(headerId);
        return u;
    }

    /**
     * Split the array into ExtraFields and populate them with the
     * give data.
     *
     * @since 1.1
     */
    public static ZipExtraField[] parse(byte[] data) throws ZipException {
        Vector v = new Vector();
        int start = 0;
        while (start <= data.length - 4) {
            ZipShort headerId = new ZipShort(data, start);
            int length = (new ZipShort(data, start + 2)).getValue();
            if (start + 4 + length > data.length) {
                throw new ZipException("data starting at " + start
                    + " is in unknown format");
            }
            try {
                ZipExtraField ze = createExtraField(headerId);
                ze.parseFromLocalFileData(data, start + 4, length);
                v.addElement(ze);
            } catch (InstantiationException ie) {
                throw new ZipException(ie.getMessage());
            } catch (IllegalAccessException iae) {
                throw new ZipException(iae.getMessage());
            }
            start += (length + 4);
        }
        if (start != data.length) { // array not exhausted
            throw new ZipException("data starting at " + start
                + " is in unknown format");
        }

        ZipExtraField[] result = new ZipExtraField[v.size()];
        v.copyInto(result);
        return result;
    }

    /**
     * Merges the local file data fields of the given ZipExtraFields.
     *
     * @since 1.1
     */
    public static byte[] mergeLocalFileDataData(ZipExtraField[] data) {
        int sum = 4 * data.length;
        for (int i = 0; i < data.length; i++) {
            sum += data[i].getLocalFileDataLength().getValue();
        }
        byte[] result = new byte[sum];
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            System.arraycopy(data[i].getHeaderId().getBytes(),
                             0, result, start, 2);
            System.arraycopy(data[i].getLocalFileDataLength().getBytes(),
                             0, result, start + 2, 2);
            byte[] local = data[i].getLocalFileDataData();
            System.arraycopy(local, 0, result, start + 4, local.length);
            start += (local.length + 4);
        }
        return result;
    }

    /**
     * Merges the central directory fields of the given ZipExtraFields.
     *
     * @since 1.1
     */
    public static byte[] mergeCentralDirectoryData(ZipExtraField[] data) {
        int sum = 4 * data.length;
        for (int i = 0; i < data.length; i++) {
            sum += data[i].getCentralDirectoryLength().getValue();
        }
        byte[] result = new byte[sum];
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            System.arraycopy(data[i].getHeaderId().getBytes(),
                             0, result, start, 2);
            System.arraycopy(data[i].getCentralDirectoryLength().getBytes(),
                             0, result, start + 2, 2);
            byte[] local = data[i].getCentralDirectoryData();
            System.arraycopy(local, 0, result, start + 4, local.length);
            start += (local.length + 4);
        }
        return result;
    }
}
