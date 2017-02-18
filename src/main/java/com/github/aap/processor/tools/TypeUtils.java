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

import com.github.aap.processor.tools.domain.ClassType;
import com.github.aap.processor.tools.types.PrimitiveTypes;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * Various static utilities aiding in the use of Types.
 *
 * @author dancc
 */
public class TypeUtils {
   
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
    public static ClassType parseClassType(Object obj) {
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
        
        return parseClassType(potentialClazz);
    }
    
    private static ClassType parseClassType(Class clazz) {
        if (clazz.getGenericSuperclass() != null) {
            
            // check if generic string already returns type info: public class java.util.ArrayList<E>
            boolean isAlreadyGeneric = clazz.toGenericString().matches(TypeUtilsConstants.CLASS_REGEX);
            if (isAlreadyGeneric) {
                String [] parts = clazz.toGenericString().split(TypeUtilsConstants.SPACE_STRING);
                return parseClassType(parts[parts.length - 1], null, null);
            } else {
                
                if (clazz.getGenericSuperclass().getTypeName().equals(TypeUtilsConstants.OBJECT_CLASS)) {
                    ClassType clazzType = parseClassType(clazz.getName());
                    if (clazz.getInterfaces().length > 0) {
                        Set<TypeToken> tt = TypeToken.of(clazz).getTypes().interfaces();
                        for (TypeToken type : tt) {
                            clazzType.add(parseClassType(type.getType()));
                        }
                    }
                    return clazzType;
                } else {
                    return parseClassType(clazz.getGenericSuperclass());
                }       
            }
        } else {
            String [] parts = clazz.toGenericString().split(TypeUtilsConstants.SPACE_STRING);
            return parseClassType(parts[parts.length - 1], null, null);
        }
    }
    
    private static ClassType parseClassType(Type type) {
        return TypeUtils.parseClassType(type.getTypeName());
    }
        
    private static ClassType parseClassType(String clazz) {
        return parseClassType(clazz, null, null);
    }
        
    private static ClassType parseClassType(String clazzAndTypes, ClassType genericTypes, StringBuilder builder) {

        int index = clazzAndTypes.indexOf(TypeUtilsConstants.GREATER_THAN);
        if (index == -1) {
            if (genericTypes != null) {
                return new ClassType(clazzAndTypes, genericTypes);
            } else {
                return new ClassType(clazzAndTypes, null);
            }
        }
        
        ClassType types = new ClassType(clazzAndTypes.substring(0, index), (genericTypes != null ? genericTypes : null));
        try {
                        
            if (builder == null) {
                builder = new StringBuilder();
            }
                        
            char[] chars = (char[]) VALUE_FIELD[0].get(clazzAndTypes);
            int stopPoint = chars.length - 2;
            int lessThanEncountered = 0;
            for (int i = index + 1; i < chars.length - 1; i++) {
                
                if (chars[i] != TypeUtilsConstants.SPACE_CHAR) {
                    builder.append(chars[i]);
                    
                    switch (chars[i]) {
                    case TypeUtilsConstants.GREATER_THAN_CHAR:
                        lessThanEncountered += 1;
                        break;
                    case TypeUtilsConstants.LESS_THAN_CHAR:
                        lessThanEncountered -= 1;
                        if (i == stopPoint) {
                            String foundType = builder.toString();  
                            builder.setLength(0);
                            ClassType type = parseClassType(foundType, types, builder);
                            types.add(type);
                        }   
                        break;
                    case TypeUtilsConstants.COMMA_CHAR:
                        if (lessThanEncountered == 0) {
                            builder.deleteCharAt(builder.length() - 1);
                            String foundType = builder.toString();
                            builder.setLength(0);
                            ClassType type = parseClassType(foundType, types, builder);
                            types.add(type);                                
                        } 
                        break;
                    default:
                        if (i == stopPoint) {
                            String foundType = builder.toString();
                            builder.setLength(0);
                            ClassType type = parseClassType(foundType, types, builder);
                            types.add(type);  
                        } 
                        break;
                    }
                }                
            }            
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw Throwables.propagate(ex);
        }

        return types;  
    }
    
    private TypeUtils() {
        throw new UnsupportedOperationException("Purposely not implemented");
    }
}