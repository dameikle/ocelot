package com.spartansoftwareinc.plugins;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Detect, install, and instantiate any available plugin classes.
 * 
 * This is meant to be used by calling discover(), and then kept 
 * around to provide access to instances of all the discovered plugins.
 * The plugins themselves are instantiated immediately and are treated
 * like stateful singletons.
 */
public class PluginManager {

	private List<String> pluginClassNames = new ArrayList<String>();
	private List<Plugin> plugins = new ArrayList<Plugin>();
	private ClassLoader classLoader;
	
	public PluginManager() {
	}
	
	/**
	 * Get a list of available plugin instances.
	 * @return
	 */
	public List<Plugin> getPlugins() {
		return plugins;
	}
	
	/**
	 * Search the provided directory for any JAR files containing valid 
	 * plugin classes.  Instantiate and configure any such classes.
	 * @param pluginDirectory
	 * @throws IOException if something goes wrong reading the directory
	 */
	public void discover(File pluginDirectory) throws IOException {
		if (!pluginDirectory.isDirectory()) {
			return;
		}
		
		File[] jarFiles = pluginDirectory.listFiles(new JarFilenameFilter());
		
		installClassLoader(jarFiles);
		
		for (File f : jarFiles) {
			scanJar(f);
		}
		
		for (String s : pluginClassNames) {
			try {
				@SuppressWarnings("unchecked")
				Class<? extends Plugin> c = (Class<Plugin>)Class.forName(s, false, classLoader);
				Plugin plugin = c.newInstance();
				plugins.add(plugin);
			}
			catch (ClassNotFoundException e) {
				// XXX Shouldn't happen?
				System.out.println("Warning: " + e.getMessage());
			}
			catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void installClassLoader(File[] jarFiles) throws IOException {
		final List<URL> pluginJarURLs = new ArrayList<URL>();
		for (File file : jarFiles) {
			// Make sure that this is actually a real jar
			if (!isValidJar(file)) {
				continue;
			}
			// TODO - this may break when the path contains whitespace
			URL url = file.toURI().toURL();
			pluginJarURLs.add(url);
		}

		classLoader = AccessController.doPrivileged(
				new PrivilegedAction<URLClassLoader>() {
					public URLClassLoader run() {
						return new URLClassLoader(
								pluginJarURLs.toArray(new URL[pluginJarURLs.size()]),
								Thread.currentThread().getContextClassLoader());
					}
				});		
	}
	
	void scanJar(final File file) {
		try {
			Enumeration<JarEntry> e = new JarFile(file).entries();
			while (e.hasMoreElements()) {
				JarEntry entry = e.nextElement();
				String name = entry.getName();
				if (name.endsWith(".class")) {
					name = convertFileNameToClass(name);
					try {
						Class<?> clazz = Class.forName(name, false, classLoader);
						// Skip non-instantiable classes
						if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
							continue;
						}
						if (Plugin.class.isAssignableFrom(clazz)) {
							// It's a plugin!  Just store the name for now
							// since we will need to reinstantiate it later with the 
							// real classloader (I think)
							if (pluginClassNames.contains(name)) {
								// TODO: log this
								System.out.println("Warning: found multiple implementations of plugin class " +
												   name);
							}
							else {
								pluginClassNames.add(name);
							}
						}
					}
					catch (ClassNotFoundException ex) {
						// XXX shouldn't happen?
						System.out.println("Warning: " + ex.getMessage());
					}
				}
			}
		}
		catch (IOException e) {
			// XXX Log this and continue
			e.printStackTrace();
		}
	}

	private boolean isValidJar(File f) throws IOException {
		JarInputStream is = new JarInputStream(new FileInputStream(f));
		boolean rv = (is.getNextEntry() != null);
		is.close();
		return rv;
	}
	
	// Convert file name to a java class name
	private String convertFileNameToClass(String filename) {
		String s = filename.substring(0, filename.length() - 6);
		return s.replace('/', '.');
	}
	
	class JarFilenameFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String filename) {
			if (filename == null || filename.equals("")) {
				return false;
			}
			int i = filename.lastIndexOf('.');
			if (i == -1) {
				return false;
			}
			String s = filename.substring(i);
			return s.equalsIgnoreCase(".jar");
		}
	}
}
