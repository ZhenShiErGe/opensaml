package com.fed.saml.sp.protocol.utils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fed.saml.sp.protocol.authn.handlers.SAMLAuthnRequest;

public class SAMLUtil {
    private static Logger logger = LoggerFactory.getLogger(SAMLUtil.class);

    private static final String CONFIG_FILE = "config/config.properties";
    
	public static Map<String, String> getConfigProperties() {
    	Properties properties = null;
    	InputStream input = null;
        Map<String, String> ssoConfigMap = null;

		if (ssoConfigMap == null) {
			try {
				properties = new Properties();
				
				input = SAMLAuthnRequest.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
				if(input==null){
					logger.error("Sorry, unable to find " + CONFIG_FILE);
				    return null;
				}
		
				//load a properties file from class path, inside static method
				properties.load(input);
				
				ssoConfigMap = new HashMap<String, String>((Map) properties);
				
		        //get the property value and print it out
				logger.info("SSO Config Map: " + ssoConfigMap);
		
			} catch (IOException ex) {
				ex.printStackTrace();
		    } finally{
		    	if(input!=null){
		    		try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		    	}
		    }
		}
    	return ssoConfigMap;
    }
	
	public static final String getRandomNumber() {
		Random random = new Random(); 		
		return Integer.toString(random.nextInt(1000));
	}
	
	public static final String getValidUntilDate() { 
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");			
		Calendar calendar = new GregorianCalendar();	
		calendar.add(Calendar.YEAR, 10);
		return sdf.format(calendar.getTime()).toString();
	}
}
