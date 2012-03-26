package org.atomhopper.repose;

import com.rackspace.papi.service.ServiceContext;
import com.rackspace.papi.service.context.ConfigurationServiceContext;
import com.rackspace.papi.service.context.EventManagerServiceContext;
import com.rackspace.papi.service.context.jndi.ServletContextHelper;
import com.rackspace.papi.service.naming.InitialServiceContextFactory;
import com.rackspace.papi.service.threading.ThreadingServiceContext;
import com.rackspace.papi.servlet.PowerApiContextException;

import java.util.Iterator;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ReposeContextManager implements ServletContextListener {

   private static final Logger LOG = LoggerFactory.getLogger(ReposeContextManager.class);
   private final LinkedList<String> boundServiceContextNames;
   private Context initialContext;

   public ReposeContextManager() {
      boundServiceContextNames = new LinkedList<String>();
   }

   private <T extends ServiceContext> void initService(T resource, ServletContextEvent sce) {
      // Bind the service first
      bindServletContextBoundService(resource);

      // Initialize the service after binding it in our naming context
      resource.contextInitialized(sce);
   }

   private <T extends ServiceContext> T bindServletContextBoundService(T resource) {
      try {
         final String serviceName = resource.getServiceName();

         initialContext.bind(serviceName, resource);
         boundServiceContextNames.add(serviceName);
      } catch (NamingException ne) {
         handleNamingException("Failed to bind, \"" + resource.getServiceName() + "\" in the JNDI initial context.", ne);
      }

      return resource;
   }

   public void handleNamingException(String message, NamingException ne) throws PowerApiContextException {
      final PowerApiContextException newException = new PowerApiContextException(message + " Reason: " + ne.getExplanation(), ne);
      LOG.error(newException.getMessage(), ne);

      throw newException;
   }

   @Override
   public void contextInitialized(ServletContextEvent sce) {
      final ServletContext servletContext = sce.getServletContext();

      try {
         this.initialContext = new InitialServiceContextFactory().getInitialContext();
      } catch (NamingException ne) {
         handleNamingException("Failed to build initial context", ne);
      }

      try {
         // Initial subcontexts
         initialContext.createSubcontext("kernel");
         initialContext.createSubcontext("services");
      } catch (NamingException ne) {
         handleNamingException("Failed to create required subcontexts in the JNDI initial context.", ne);
      }

      // Most bootstrap steps require or will try to load some kind of
      // configuration so we need to set our naming context in the servlet context
      // first before anything else
      ServletContextHelper.setPowerApiContext(servletContext, initialContext);

      // Services Bootstrap

      // Threading Service
      initService(new ThreadingServiceContext(), sce);

      // Event kernel init
      initService(new EventManagerServiceContext(), sce);

      // Configuration Services
      initService(new ConfigurationServiceContext(), sce);

      // Logging Service
      // initService(new LoggingServiceContext(), sce);
   }

   @Override
   public void contextDestroyed(ServletContextEvent sce) {
      final Iterator<String> iterator = boundServiceContextNames.descendingIterator();

      while (iterator.hasNext()) {
         final String ctxName = iterator.next();

         try {
            final ServiceContext ctx = (ServiceContext) initialContext.lookup(ctxName);
            initialContext.unbind(ctxName);

            ctx.contextDestroyed(sce);
         } catch (NamingException ne) {
            handleNamingException("Unable to destroy service context \"" + ctxName + "\" - Reason: " + ne.getMessage(), ne);
         }
      }
   }
}