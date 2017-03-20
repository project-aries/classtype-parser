/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.aap.processor.tools;

import com.github.aap.processor.tools.utils.Constants;
import com.github.aap.processor.tools.domain.ClassType;
import com.github.aap.processor.tools.types.PrimitiveTypes;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import javax.lang.model.SourceVersion;

/**
 * Various static utilities aiding in the use of Types.
 *
 * @author dancc
 */
public class ClassTypeParser {
   
    private static final Field[] VALUE_FIELD = new Field[1];
    
    static {
        try {
            VALUE_FIELD[0] = String.class.getDeclaredField("value");
            VALUE_FIELD[0].setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            // ignore as we know the field exists
        }
    }
  
    /**
     * Turns some Type (e.g. Class, Object, etc.) into a ClassType.
     * 
     * @param obj arbitrary object
     * @return ClassType
     */
    public static ClassType parse(final Object obj) {
        Class potentialClazz;
        if (obj != null) {
            if (obj instanceof Class) {
                potentialClazz = (Class)obj;
                if (potentialClazz.isPrimitive()) {
                    potentialClazz = PrimitiveTypes.from(potentialClazz.toGenericString()).getBoxedClass();
                } 
            } else {
                potentialClazz = obj.getClass();
            }
        } else {
            potentialClazz = PrimitiveTypes.from(obj).getBoxedClass();
        }
        
        return parse(potentialClazz);
    }
    
    private static ClassType parse(final Class clazz) {
        if (clazz.getGenericSuperclass() != null) {
            
            // check if generic string already returns type info: public class java.util.ArrayList<E>
            final boolean isAlreadyGeneric = clazz.toGenericString().matches(Constants.CLASS_REGEX);
            if (isAlreadyGeneric) {
                final String [] parts = clazz.toGenericString().split(Constants.SPACE_STRING);
                return parse(parts[parts.length - 1], null, null);
            } else {
                if (clazz.getGenericSuperclass().getTypeName().equals(Constants.OBJECT_CLASS)) {
                    final ClassType clazzType = parse(clazz.getName());
                    if (clazz.getInterfaces().length > 0) {
                        for (final Type possibleType : clazz.getGenericInterfaces()) {
                            clazzType.add(parse(possibleType.getTypeName(), clazzType, null));
                        }
                    }
                    return clazzType;
                } else {
                    return parse(clazz.getGenericSuperclass());
                }       
            }
        } else {
            final String [] parts = clazz.toGenericString().split(Constants.SPACE_STRING);
            return parse(parts[parts.length - 1], null, null);
        }
    }
        
    private static ClassType parse(final String clazz) {
        return parse(clazz, null, null);
    }
        
    private static ClassType parse(final String clazzAndTypes, final ClassType parentType, StringBuilder builder) {

        if (SourceVersion.isName(clazzAndTypes) 
                && clazzAndTypes.indexOf(Constants.PERIOD_CHAR) == -1) {
            try {
                Class.forName(clazzAndTypes);
            } catch (ClassNotFoundException cnfe) {
                return new ClassType(Constants.OBJECT_CLASS, parentType);
            }
        }
        
        final int index = clazzAndTypes.indexOf(Constants.GREATER_THAN);
        if (index == -1) {
            return new ClassType(clazzAndTypes, parentType);
        } 
        
        final ClassType classType = new ClassType(clazzAndTypes.substring(0, index), parentType);
        try {
                        
            if (builder == null) {
                builder = new StringBuilder();
            }
                        
            final char[] chars = (char[]) VALUE_FIELD[0].get(clazzAndTypes);
            final int stopPoint = chars.length - 2;
            int lessThanEncountered = 0;
            for (int i = index + 1; i < chars.length - 1; i++) {
                
                if (chars[i] != Constants.SPACE_CHAR) {
                    builder.append(chars[i]);
                    
                    switch (chars[i]) {
                    case Constants.GREATER_THAN_CHAR:
                        lessThanEncountered += 1;
                        break;
                    case Constants.LESS_THAN_CHAR:
                        lessThanEncountered -= 1;
                        if (i == stopPoint) {
                            final String foundType = builder.toString();  
                            builder.setLength(0);
                            final ClassType type = parse(foundType, classType, builder);
                            classType.add(type);
                        }   
                        break;
                    case Constants.COMMA_CHAR:
                        if (lessThanEncountered == 0) {
                            builder.deleteCharAt(builder.length() - 1);
                            final String foundType = builder.toString();
                            builder.setLength(0);
                            final ClassType type = parse(foundType, classType, builder);
                            classType.add(type);                                
                        } 
                        break;
                    default:
                        if (i == stopPoint) {
                            final String foundType = builder.toString();
                            builder.setLength(0);
                            final ClassType type = parse(foundType, classType, builder);
                            classType.add(type);  
                        } 
                        break;
                    }
                }                
            }            
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        }

        return classType;  
    }
    
    private ClassTypeParser() {
        throw new UnsupportedOperationException("Purposely not implemented");
    }
}