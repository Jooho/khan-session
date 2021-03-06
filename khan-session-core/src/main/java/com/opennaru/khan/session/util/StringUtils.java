/*
 * Opennaru, Inc. http://www.opennaru.com/
 *
 *  Copyright (C) 2014 Opennaru, Inc. and/or its affiliates.
 *  All rights reserved by Opennaru, Inc.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.opennaru.khan.session.util;

/**
 * String Null Check Utility
 *
 * @author Junshik Jeon(service@opennaru.com, nameislocus@gmail.com)
 */
public class StringUtils {

    /**
     * 값이 Null이면 Exception을 throw
     * @param name
     * @param value
     */
    public static void isNotNull(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(name + "s Null !");
        }
    }

    /**
     * 값이 Null이면 Exception을 throw
     * @param name
     * @param value
     */
    public static void isNotNull(String name, String value) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("Argument " + name + " should not empty !");
        }
    }

    /**
     * 값이 Null이나 ""이면 true 반환
     * @param value
     * @return
     */
    public static boolean isNullOrEmpty(String value) {
        if (value == null || value.trim().length() == 0) {
            return true;
        } else {
            return false;
        }
    }

}