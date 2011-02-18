package com.avaje.ebean.enhance.maven;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import com.avaje.ebean.enhance.agent.Transformer;
import com.avaje.ebean.enhance.ant.OfflineFileTransform;
import com.avaje.ebean.enhance.ant.TransformationListener;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.model.Dependency;

/**
 * A Maven Plugin that can enhance entity beans etc for use by Ebean.
 * <p>
 * You can use this plugin as part of your build process to enhance entity beans etc.
 * </p>
 * <p>
 * The parameters are:
 * <ul>
 * <li><b>classSource</b> This is the root directory where the .class files are found.</li>
 * <li><b>classDestination</b> This is the root directory where the .class files are written to. If this is left out
 * then this defaults to the <b>classSource</b>.</li>
 * <li><b>packages</b> A comma delimited list of packages that is searched for classes that need to be enhanced. If the
 * package ends with ** or * then all subpackages are also searched.</li>
 * <li><b>transformArgs</b> Arguments passed to the transformer. Typically a debug level in the form of debug=1 etc.</li>
 * </ul>
 * </p>
 * 
 * <pre class="code">
 * 
 *	&lt;plugin&gt;
 *	  &lt;groupId&gt;org.avaje&lt;/groupId&gt;
 *	  &lt;artifactId&gt;ebean-maven-enhancement-plugin&lt;/artifactId&gt;
 *	  &lt;version&gt;2.5&lt;/version&gt;
 *	  &lt;executions&gt;
 *		&lt;execution&gt;
 *		  &lt;id&gt;main&lt;/id&gt;
 *		  &lt;phase&gt;process-classes&lt;/phase&gt;
 *		  &lt;goals&gt;
 *			&lt;goal&gt;enhance&lt;/goal&gt;
 *		  &lt;/goals&gt;
 *		&lt;/execution&gt;
 *	  &lt;/executions&gt;
 *	  &lt;configuration&gt;
 *		&lt;classSource&gt;target/classes&lt;/classSource&gt;
 *		&lt;packages&gt;com.avaje.ebean.meta.**, com.acme.myapp.entity.**&lt;/packages&gt;
 *		&lt;transformArgs&gt;debug=1&lt;/transformArgs&gt;
 *	  &lt;/configuration&gt;
 *	&lt;/plugin&gt;
 * </pre>
 * <p>
 * To invoke explicitly:<br/>
 * <code>
 * &nbsp;&nbsp;&nbsp;&nbsp;mvn ebean-enhancer:enhance
 * </code>
 * </p>
 * 
 * @author Paul Mendelson
 * @version $Revision$, $Date$
 * @since 2.5, Mar, 2009
 * @see com.avaje.ebean.enhance.ant.AntEnhanceTask
 * @goal enhance
 * @phase process-classes
 */
public class MavenEnhanceTask extends AbstractMojo {
	/**
	 * the classpath used to search for e.g. inerited classes
	 * 
	 * @parameter default-value="${project}"
	 */
	MavenProject proj;

	/** @component */
	private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

	/**
	 * @parameter expression="${mojoExecution}"
	 */
	private org.apache.maven.plugin.MojoExecution execution;

	/** @component */
	private org.apache.maven.artifact.resolver.ArtifactResolver resolver;

	/** @parameter default-value="${localRepository}" */
	private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

	/** @parameter default-value="${project.remoteArtifactRepositories}" */
	private java.util.List remoteRepositories;

	/** @parameter default-value="${project.distributionManagementArtifactRepository}" */
	private ArtifactRepository deploymentRepository;
	/**
	 * Desired scope (either compile or test)
	 * 
	 * @parameter scope
	 */
	private String scope;
	/**
	 * the classpath used to search for e.g. inerited classes
	 * 
	 * @parameter
	 */
	private String classpath;

	/**
	 * Set the directory holding the class files we want to transform.
	 * 
	 * @parameter
	 */
	private String classSource;

	/**
	 * Set the destination directory where we will put the transformed classes.
	 * <p>
	 * This is commonly the same as the classSource directory.
	 * </p>
	 * 
	 * @parameter
	 */
	String classDestination;

	/**
	 * Set the arguments passed to the transformer.
	 * 
	 * @parameter
	 */
	String transformArgs;

	/**
	 * Set the package name to search for classes to transform.
	 * <p>
	 * If the package name ends in "/**" then this recursively transforms all sub packages as well.
	 * </p>
	 * 
	 * @parameter
	 */
	String packages;

	public void execute() throws MojoExecutionException {
		final Log log = getLog();
        if(scope==null) {
            if(execution.getExecutionId().toLowerCase().contains("test"))
                scope="test-";
            else
                scope="";
        }
		if (classSource == null)
			classSource = "target/"+scope+"classes";
		if (classDestination == null)
			classDestination = classSource;
		File f = new File("");
		log.info("Current Directory: " + f.getAbsolutePath());

		StringBuilder extraClassPath = new StringBuilder();
		List dependencySet = proj.getDependencies();
		for (Iterator i = dependencySet.iterator(); i.hasNext();) {
			Dependency d = (Dependency) i.next();
			if (inScope(d)) {
			Artifact artifact = artifactFactory.createArtifact(d.getGroupId(), d.getArtifactId(), d.getVersion(),
					d.getClassifier(), "jar");
				appendArtifact(extraClassPath, artifact);
			}
		}
		extraClassPath.append(classSource);
		if (classpath != null) {
			if (!extraClassPath.toString().endsWith(";")) {
				extraClassPath.append(";");
			}
			extraClassPath.append(classpath);
		}
		Transformer t = new Transformer(extraClassPath.toString(), transformArgs);
		ClassLoader cl = MavenEnhanceTask.class.getClassLoader();
		log.info("classSource=" + classSource + "  transformArgs=" + transformArgs + "  classDestination="
				+ classDestination + "  packages=" + packages);
		OfflineFileTransform ft = new OfflineFileTransform(t, cl, classSource, classDestination);
		ft.setListener(new TransformationListener() {

			public void logEvent(String msg) {
				log.info(msg);
			}

			public void logError(String msg) {
			}
		});
		ft.process(packages);
	}

	private boolean inScope(Dependency d) {
		boolean ok = true;
		if (!scope.equalsIgnoreCase("test-"))
			ok=! "test".equalsIgnoreCase(d.getScope());
		if (!ok)
			getLog().info("skipping " + d.getArtifactId());
		return ok;
	}

	private void appendArtifact(StringBuilder extraClassPath, Artifact artifact) {
		try {
			resolver.resolve(artifact, remoteRepositories, localRepository);
			File artifactFile = artifact.getFile();
			getLog().info("arg=" + artifactFile.getAbsolutePath());
			extraClassPath.append(artifactFile.getAbsolutePath() + ";");
		} catch (ArtifactResolutionException e) {
			getLog().info(e.getMessage());
		} catch (ArtifactNotFoundException e) {
			getLog().info(e.getMessage());
		}
	}
}