// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.plugincontext;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.logging.TraceContext;

/**
 * Context for invoking plugin extensions.
 *
 * <p>Invoking a plugin extension through a PluginContext sets a logging tag with the plugin name is
 * set. This way any errors that are triggered by the plugin extension (even if they happen in
 * Gerrit code which is called by the plugin extension) can be easily attributed to the plugin.
 *
 * <p>If possible plugin extensions should be invoked through:
 *
 * <ul>
 *   <li>{@link PluginItemContext} for extensions from {@link DynamicItem}
 *   <li>{@link PluginSetContext} for extensions from {@link DynamicSet}
 *   <li>{@link PluginMapContext} for extensions from {@link DynamicMap}
 * </ul>
 *
 * <p>A plugin context can be manually opened by invoking the newTrace methods. This should only be
 * needed if an extension throws multiple exceptions that need to be handled:
 *
 * <pre>
 * public interface Foo {
 *   void doFoo() throws Exception1, Exception2, Exception3;
 * }
 *
 * ...
 *
 * for (Extension<Foo> fooExtension : fooDynamicMap) {
 *   try (TraceContext traceContext = PluginContext.newTrace(fooExtension)) {
 *     fooExtension.get().doFoo();
 *   }
 * }
 * </pre>
 *
 * <p>This class hosts static methods with generic functionality to invoke plugin extensions with a
 * trace context that are commonly used by {@link PluginItemContext}, {@link PluginSetContext} and
 * {@link PluginMapContext}.
 *
 * <p>The run* methods execute an extension but don't deliver a result back to the caller.
 * Exceptions can be caught and logged.
 *
 * <p>The call* methods execute an extension and deliver a result back to the caller.
 */
public class PluginContext<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FunctionalInterface
  public interface ExtensionImplConsumer<T> {
    void run(T t) throws Exception;
  }

  @FunctionalInterface
  public interface ExtensionImplFunction<T, R> {
    R call(T input);
  }

  @FunctionalInterface
  public interface CheckedExtensionImplFunction<T, R, X extends Exception> {
    R call(T input) throws X;
  }

  @FunctionalInterface
  public interface ExtensionConsumer<T extends Extension<?>> {
    void run(T extension) throws Exception;
  }

  @FunctionalInterface
  public interface ExtensionFunction<T extends Extension<?>, R> {
    R call(T extension);
  }

  @FunctionalInterface
  public interface CheckedExtensionFunction<T extends Extension<?>, R, X extends Exception> {
    R call(T extension) throws X;
  }

  /**
   * Opens a new trace context for invoking a plugin extension.
   *
   * @param dynamicItem dynamic item that holds the extension implementation that is being invoked
   *     from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(DynamicItem<T> dynamicItem) {
    Extension<T> extension = dynamicItem.getEntry();
    if (extension == null) {
      return TraceContext.open();
    }
    return newTrace(extension);
  }

  /**
   * Opens a new trace context for invoking a plugin extension.
   *
   * @param extension extension that is being invoked from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(Extension<T> extension) {
    return TraceContext.open().addPluginTag(checkNotNull(extension).getPluginName());
  }

  /**
   * Runs a plugin extension. All exceptions from the plugin extension are caught and logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * @param extension extension that is being invoked
   * @param extensionImplConsumer the consumer that invokes the extension
   */
  static <T> void runLogExceptions(
      Extension<T> extension, ExtensionImplConsumer<T> extensionImplConsumer) {
    T extensionImpl = extension.get();
    if (extensionImpl == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(extension)) {
      extensionImplConsumer.run(extensionImpl);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionImpl.getClass(), extension.getPluginName());
    }
  }

  /**
   * Runs a plugin extension. All exceptions from the plugin extension are caught and logged.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extension extension that is being invoked
   * @param extensionConsumer the consumer that invokes the extension
   */
  static <T> void runLogExceptions(
      Extension<T> extension, ExtensionConsumer<Extension<T>> extensionConsumer) {
    T extensionImpl = extension.get();
    if (extensionImpl == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(extension)) {
      extensionConsumer.run(extension);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionImpl.getClass(), extension.getPluginName());
    }
  }

  /**
   * Runs a plugin extension. All exceptions from the plugin extension except exceptions of the
   * specified type are caught and logged. Exceptions of the specified type are thrown and must be
   * handled by the caller.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * @param extension extension that is being invoked
   * @param extensionImplConsumer the consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  static <T, X extends Exception> void runLogExceptions(
      Extension<T> extension,
      ExtensionImplConsumer<T> extensionImplConsumer,
      Class<X> exceptionClass)
      throws X {
    T extensionImpl = extension.get();
    if (extensionImpl == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(extension)) {
      extensionImplConsumer.run(extensionImpl);
    } catch (Throwable e) {
      Throwables.throwIfInstanceOf(e, exceptionClass);
      Throwables.throwIfUnchecked(e);
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin invoke%s", extensionImpl.getClass(), extension.getPluginName());
    }
  }

  /**
   * Runs a plugin extension. All exceptions from the plugin extension except exceptions of the
   * specified type are caught and logged. Exceptions of the specified type are thrown and must be
   * handled by the caller.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extension extension that is being invoked
   * @param extensionConsumer the consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  static <T, X extends Exception> void runLogExceptions(
      Extension<T> extension,
      ExtensionConsumer<Extension<T>> extensionConsumer,
      Class<X> exceptionClass)
      throws X {
    T extensionImpl = extension.get();
    if (extensionImpl == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(extension)) {
      extensionConsumer.run(extension);
    } catch (Throwable e) {
      Throwables.throwIfInstanceOf(e, exceptionClass);
      Throwables.throwIfUnchecked(e);
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionImpl.getClass(), extension.getPluginName());
    }
  }

  /**
   * Calls a plugin extension and returns the result from the plugin extension call.
   *
   * <p>The function gets the extension implementation provided that should be invoked.
   *
   * @param extension extension that is being invoked
   * @param extensionImplFunction function that invokes the extension
   * @return the result from the plugin extension
   */
  static <T, R> R call(Extension<T> extension, ExtensionImplFunction<T, R> extensionImplFunction) {
    try (TraceContext traceContext = newTrace(extension)) {
      return extensionImplFunction.call(extension.get());
    }
  }

  /**
   * Calls a plugin extension and returns the result from the plugin extension call. Exceptions of
   * the specified type are thrown and must be handled by the caller.
   *
   * <p>The function gets the extension implementation provided that should be invoked.
   *
   * @param extension extension that is being invoked
   * @param checkedExtensionImplFunction function that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin extension
   * @throws X expected exception from the plugin extension
   */
  static <T, R, X extends Exception> R call(
      Extension<T> extension,
      CheckedExtensionImplFunction<T, R, X> checkedExtensionImplFunction,
      Class<X> exceptionClass)
      throws X {
    try (TraceContext traceContext = newTrace(extension)) {
      try {
        return checkedExtensionImplFunction.call(extension.get());
      } catch (Exception e) {
        // The only exception that can be thrown is X, but we cannot catch X since it is a generic
        // type.
        Throwables.throwIfInstanceOf(e, exceptionClass);
        Throwables.throwIfUnchecked(e);
        throw new IllegalStateException("unexpected exception: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Calls a plugin extension and returns the result from the plugin extension call.
   *
   * <p>The function get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extension extension that is being invoked
   * @param extensionFunction function that invokes the extension
   * @return the result from the plugin extension
   */
  static <T, R> R call(
      Extension<T> extension, ExtensionFunction<Extension<T>, R> extensionFunction) {
    try (TraceContext traceContext = newTrace(extension)) {
      return extensionFunction.call(extension);
    }
  }

  /**
   * Calls a plugin extension and returns the result from the plugin extension call. Exceptions of
   * the specified type are thrown and must be handled by the caller.
   *
   * <p>The function get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extension extension that is being invoked
   * @param checkedExtensionFunction function that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin extension
   * @throws X expected exception from the plugin extension
   */
  static <T, R, X extends Exception> R call(
      Extension<T> extension,
      CheckedExtensionFunction<Extension<T>, R, X> checkedExtensionFunction,
      Class<X> exceptionClass)
      throws X {
    try (TraceContext traceContext = newTrace(extension)) {
      try {
        return checkedExtensionFunction.call(extension);
      } catch (Exception e) {
        // The only exception that can be thrown is X, but we cannot catch X since it is a generic
        // type.
        Throwables.throwIfInstanceOf(e, exceptionClass);
        Throwables.throwIfUnchecked(e);
        throw new IllegalStateException("unexpected exception: " + e.getMessage(), e);
      }
    }
  }
}
