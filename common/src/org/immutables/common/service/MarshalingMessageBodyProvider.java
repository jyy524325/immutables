/*
    Copyright 2013 Immutables.org authors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.immutables.common.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.immutables.annotation.GenerateImmutable;
import org.immutables.annotation.GenerateMarshaler;
import org.immutables.common.marshal.Marshaler;
import org.immutables.common.marshal.internal.MarshalingSupport;

/**
 * JSON marshaling JAX-RS provider for immutable classes with generated marshaler.
 * @see GenerateImmutable
 * @see GenerateMarshaler
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MarshalingMessageBodyProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
  public static final JsonFactory jsonFactory = new JsonFactory();

  final LoadingCache<Class<?>, Marshaler<?>> marshalerCache = CacheBuilder.newBuilder()
      .build(new CacheLoader<Class<?>, Marshaler<?>>() {
        @Override
        public Marshaler<?> load(Class<?> type) throws Exception {
          return MarshalingSupport.loadMarshalerFor(type);
        }
      });

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    if (MarshalingSupport.hasAssociatedMarshaler(type)) {
      marshalerCache.refresh(type);
      return true;
    }
    return false;
  }

  @Override
  public Object readFrom(
      Class<Object> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, String> httpHeaders,
      InputStream entityStream) throws IOException, WebApplicationException {

    try (JsonParser parser = jsonFactory.createParser(entityStream)) {
      Marshaler<?> marshaler = marshalerCache.getUnchecked(type);
      parser.nextToken();
      return marshaler.unmarshalInstance(parser);
    } catch (IOException e) {
      throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
    }
  }

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    if (MarshalingSupport.hasAssociatedMarshaler(type)) {
      marshalerCache.refresh(type);
      return true;
    }
    return false;
  }

  @Override
  public long getSize(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(
      Object o,
      Class<?> actualType,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders,
      OutputStream entityStream) throws IOException, WebApplicationException {

    if (genericType instanceof Class<?>) {
      Class<?> declaredType = (Class<?>) genericType;
      try (JsonGenerator generator = jsonFactory.createGenerator(entityStream)) {
        @SuppressWarnings("unchecked")
        Marshaler<Object> marshaler =
            (Marshaler<Object>) marshalerCache.getUnchecked(declaredType);

        marshaler.marshalInstance(generator, o);
      }
    }
  }
}