/**
 * This file is part of Semtinel (http://www.semtinel.org).
 * Copyright (c) 2007-2010 Kai Eckert (http://www.kaiec.org).
 *
 * Semtinel is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Semtinel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Semtinel. If not, see <http://www.gnu.org/licenses/>.
 */

package org.semtinel.core.shell;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.semtinel.core.shell.util.ConversionException;

import static org.semtinel.core.shell.util.ConversionUtils.*;
import static org.semtinel.core.shell.util.ReflectionUtils.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Alexander Hanschke <dev@alexander-hanschke.de>
 * @version 30.07.2010
 */
public class CommandRunner {

    @Inject
    private CommandExecutionStrategy strategy;

    @Inject
    private Shell shell;

    private Collection<Method> commandAnnotatedMethods;

    private Collection<Class<?>> supportedParameterTypes;

    {
        supportedParameterTypes = new HashSet<Class<?>>();

        supportedParameterTypes.add(int.class);
        supportedParameterTypes.add(long.class);
        supportedParameterTypes.add(float.class);
        supportedParameterTypes.add(double.class);
        supportedParameterTypes.add(char.class);
        supportedParameterTypes.add(String.class);
    }

    private Collection<Method> getCommandAnnotatedMethods() {
        if (commandAnnotatedMethods == null) {
            commandAnnotatedMethods = getMethodsAnnotatedWith(strategy, Command.class);
        }

        return commandAnnotatedMethods;
    }

    public void executeCommand(String commandName, Map<String, String> options) {
        if (commandName.isEmpty()) {
            return;
        }
        
        Method mappedMethod;

        try {
            mappedMethod = getCommandMethodForCommandName(commandName);
        } catch (CommandNotBoundException e) {
            shell.flashMessage(e.getMessage());
            return;
        }

        Command command = mappedMethod.getAnnotation(Command.class);

        try {
            if (isNoArgsMethod(mappedMethod)) {
                invokeMethod(mappedMethod);
            } else {
                invokeMethodWithOptions(mappedMethod, options);
            }
        } catch (CommandExecutionException e) {
            shell.flashMessage(
                    String.format("could not execute command '%s' [%s]", command.name(), e.getMessage())
            );
            shell.flashMessage(String.format("usage: %s", command.usage()));
        }
    }

    private void invokeMethodWithOptions(Method method, Map<String, String> options) {
        Annotation[][] annotations = method.getParameterAnnotations();
        Class<?>[] parameterTypes = method.getParameterTypes();

        List<Object> invocationParameters = new ArrayList<Object>();

        Preconditions.checkState(annotations.length == parameterTypes.length);

        int index = 0;
        for (Annotation[] annotationsOnType : annotations) {
            for (Annotation annotationOnType : annotationsOnType) {
                if (annotationOnType instanceof Option) {
                    String key = ((Option) annotationOnType).key();

                    if (options.containsKey(key)) {
                        try {
                            Class<?> parameterType = parameterTypes[index];

                            if (supportsParameterType(parameterType)) {
                                invocationParameters.add(parseToType(options.get(key), parameterType));
                            } else {
                                throw new UnsupportedParameterTypeException(parameterType);
                            }
                        } catch (ConversionException e) {
                            throw new CommandBindingException(
                                    String.format("cannot assign '%s' to '%s'!", options.get(key), parameterTypes[index])
                            );
                        }
                    }
                }
            }
            
            index++;
        }

        invokeMethod(method, invocationParameters.toArray());
    }

    private void invokeMethod(Method method, Object... args) {
        try {
            method.invoke(strategy, args);
        } catch (IllegalAccessException e1) {
            throw new CommandExecutionException(String.format("method %s is not accessible!", method.getName()), e1);
        } catch (IllegalArgumentException e2) {
            throw new CommandExecutionException(String.format("invalid arguments for method %s!", method.getName()), e2);
        }  catch (InvocationTargetException e3) {
            throw new CommandExecutionException(String.format("invocation target exception for method %s", method.getName()), e3);
        } 
    }

    private Method getCommandMethodForCommandName(String commandName) {
        for (Method method : getCommandAnnotatedMethods()) {
            if (method.getAnnotation(Command.class).name().equals(commandName)) {
                return method;
            }
        }

        throw new CommandNotBoundException(
                String.format("command '%s' is not bound!", commandName)
        );
    }

    private boolean supportsParameterType(Class<?> parameterType) {
        return supportedParameterTypes.contains(parameterType);
    }
}