/* 
Copyright (c) 2010, NHIN Direct Project
All rights reserved.

Authors:
   Greg Meyer      gm2552@cerner.com
 
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
in the documentation and/or other materials provided with the distribution.  Neither the name of the The NHIN Direct Project (nhindirect.org). 
nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.nhindirect.config.resources;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nhindirect.config.model.Certificate;
import org.nhindirect.config.model.utils.CertUtils;
import org.nhindirect.config.resources.util.EntityModelConversion;
import org.nhindirect.config.store.dao.CertificateDao;
import org.nhindirect.stagent.cert.Thumbprint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.inject.Singleton;

@Component
@Path("certificate/")
@Singleton
public class CertificateResource 
{
	protected static final CacheControl noCache;
	
    private static final Log log = LogFactory.getLog(CertificateResource.class);
	
    static
	{
		noCache = new CacheControl();
		noCache.setNoCache(true);
	}
    
    protected CertificateDao certDao;
    
    /**
     * Constructor
     */
    public CertificateResource()
    {
		
	}
    
    @Autowired
    public void setCertificateDao(CertificateDao certDao) 
    {
        this.certDao = certDao;
    }
    
    @Produces(MediaType.APPLICATION_JSON)       
    @GET
    public Response getAllCertificates()
    {
		
		return getCertificatesByOwner(null);
    }
    
    @Path("{owner}")
    @Produces(MediaType.APPLICATION_JSON)       
    @GET
    public Response getCertificatesByOwner(@PathParam("owner") String owner)
    {
    	List<org.nhindirect.config.store.Certificate> retCertificates;
    	
    	try
    	{
    		retCertificates = certDao.list(owner);
    		if (retCertificates.isEmpty())
    			return Response.status(Status.NO_CONTENT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up certificates.", e);
    		return Response.serverError().cacheControl(noCache).build();
    	}
    	
    	final Collection<Certificate> modelCerts = new ArrayList<Certificate>();
    	for (org.nhindirect.config.store.Certificate cert: retCertificates)
    	{
    		modelCerts.add(EntityModelConversion.toModelCertificate(cert));
    	}
    	
		final GenericEntity<Collection<Certificate>> entity = new GenericEntity<Collection<Certificate>>(modelCerts) {};
		
		return Response.ok(entity).cacheControl(noCache).build();  
    }  
    
    @Path("{owner}/{thumbprint}")
    @Produces(MediaType.APPLICATION_JSON)       
    @GET
    public Response getCertificatesByOwnerAndThumbprint(@PathParam("owner") String owner, 
    		@PathParam("thumbprint") String thumbprint)
    {
    	org.nhindirect.config.store.Certificate retCertificate;
    	
    	try
    	{
    		retCertificate = certDao.load(owner, thumbprint);
    		if (retCertificate == null)
    			return Response.status(Status.NOT_FOUND).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up certificate.", e);
    		return Response.serverError().cacheControl(noCache).build();
    	}

		return Response.ok(EntityModelConversion.toModelCertificate(retCertificate)).cacheControl(noCache).build();  
    }  
    
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)      
    public Response addCertificate(@Context UriInfo uriInfo, Certificate cert)
    {
    	// check to see if it already exists
    	try
    	{
    		if (certDao.load(cert.getOwner(), Thumbprint.toThumbprint(CertUtils.toX509Certificate(cert.getData())).toString()) != null)
    			return Response.status(Status.CONFLICT).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error looking up certificate.", e);
    		return Response.serverError().cacheControl(noCache).build();
    	}
    
    	try
    	{
    		// get the owner if it doesn't alreay exists
			if ((cert.getOwner() == null || cert.getOwner().isEmpty()))
			{
				final CertUtils.CertContainer cont = CertUtils.toCertContainer(cert.getData());
				if (cont != null && cont.getCert() != null)
				{
					
					// now get the owner info from the cert
					final String theOwner = CertUtils.getOwner(cont.getCert());

					if (theOwner != null && !theOwner.isEmpty())
						cert.setOwner(theOwner);
				}
			}
    		
			final org.nhindirect.config.store.Certificate entCert = EntityModelConversion.toEntityCertificate(cert);
    		certDao.save(entCert);
    		
    		final UriBuilder newLocBuilder = uriInfo.getBaseUriBuilder();
    		final URI newLoc = newLocBuilder.path("certificate/" + entCert.getOwner()  + "/" + entCert.getThumbprint()).build();
    		
    		return Response.created(newLoc).cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error adding certificate.", e);
    		return Response.serverError().cacheControl(noCache).build();
    	}
    }
    
    @Path("ids/{ids}")
    @DELETE
    public Response removeCertificatesByIds(@PathParam("ids") String ids)
    {
    	final String[] idArray = ids.split(",");
    	final List<Long> idList = new ArrayList<Long>();
    	
    	
    	try
    	{
    		for (String id : idArray)
    			idList.add(Long.parseLong(id));
    		
    		certDao.delete(idList);
    		
    		return Response.ok().cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error removing certificates by ids.", e);
    		return Response.serverError().cacheControl(noCache).build();
    	}
    }
    
    @DELETE
    @Path("{owner}")   
    public Response removeCertificatesByOwner(@PathParam("owner") String owner)
    {
    	try
    	{
    		certDao.delete(owner);
    		
    		return Response.ok().cacheControl(noCache).build();
    	}
    	catch (Exception e)
    	{
    		log.error("Error removing certificates by owner.", e);
    		return Response.serverError().cacheControl(noCache).build();
    	}
    }    
}
