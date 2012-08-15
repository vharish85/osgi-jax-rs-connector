/*******************************************************************************
 * Copyright (c) 2012 EclipseSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Holger Staudacher - initial API and implementation
 ******************************************************************************/
package com.eclipsesource.jaxrs.connector.internal;

import java.util.List;

import javax.servlet.ServletException;
import javax.ws.rs.core.Request;

import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import com.sun.jersey.server.impl.container.WebApplicationProviderImpl;
import com.sun.jersey.spi.container.servlet.ServletContainer;


public class JerseyContext {

  private final Application application;
  private final ServletContainer servletContainer;
  private final HttpService httpService;
  private final String rootPath;
  private boolean isApplicationRegistered;

  public JerseyContext( HttpService httpService, String rootPath ) {
    this.httpService = httpService;
    this.rootPath = rootPath == null ? "/services" : rootPath;
    this.application = new Application();
    this.servletContainer = new ServletContainer( application );
  }

  public void addResource( Object resource ) {
    getRootApplication().addResource( resource );
    registerServletWhenNotAlreadyRegistered();
    if( isApplicationRegistered ) {
      ClassLoader original = getContextClassloader();
      try {
        Thread.currentThread().setContextClassLoader( Request.class.getClassLoader() );
        getServletContainer().reload();
      } finally {
        resetContextClassloader( original );
      }
    }
  }

  void registerServletWhenNotAlreadyRegistered() {
    if( !isApplicationRegistered ) {
      isApplicationRegistered = true;
      registerApplication();
    }
  }

  private void registerApplication() {
    ClassLoader loader = getContextClassloader();
    setContextClassloader();
    try {
      registerServlet();
    } catch( ServletException shouldNotHappen ) {
      throw new IllegalStateException( shouldNotHappen );
    } catch( NamespaceException shouldNotHappen ) {
      throw new IllegalStateException( shouldNotHappen );
    } finally {
      resetContextClassloader( loader );
    }
  }

  private ClassLoader getContextClassloader() {
    return Thread.currentThread().getContextClassLoader();
  }

  private void setContextClassloader() {
    Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
  }

  private void registerServlet() throws ServletException, NamespaceException {
    ClassLoader original = getContextClassloader();
    try {
      Thread.currentThread().setContextClassLoader( WebApplicationProviderImpl.class.getClassLoader() );
      httpService.registerServlet( rootPath, 
                                   getServletContainer(), 
                                   null, 
                                   null );
    } finally {
      resetContextClassloader( original );
    }
  }

  private void resetContextClassloader( ClassLoader loader ) {
    Thread.currentThread().setContextClassLoader( loader );
  }
  
  public void removeResource( Object resource ) {
    getRootApplication().removeResource( resource );
    if( isApplicationRegistered ) {
      getServletContainer().reload();
    }
    unregisterServletWhenNoresourcePresents();
  }

  private void unregisterServletWhenNoresourcePresents() {
    if( !getRootApplication().hasResources() ) {
      httpService.unregister( rootPath );
      isApplicationRegistered = false;
    }
  }

  public List<Object> eliminate() {
    getServletContainer().destroy();
    try {
      httpService.unregister( rootPath );
    } catch( IllegalArgumentException iae ) {
      // do nothing
    }
    return getRootApplication().getResources();
  }

  // For testing purpose
  ServletContainer getServletContainer() {
    return servletContainer;
  }
  
  // For testing purpose
  Application getRootApplication() {
    return application;
  }

}
