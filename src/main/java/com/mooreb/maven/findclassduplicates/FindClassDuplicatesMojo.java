package com.mooreb.maven.findclassduplicates;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "findclassduplicates")
public class FindClassDuplicatesMojo extends AbstractMojo {
    @Parameter (property="project.build.directory")
    private String buildDirectory;

    @Parameter (property="project.build.finalName")
    private String buildFinalName;

    @Parameter (property="project.packaging")
    private String packaging;

    @Override
    public void execute() throws MojoExecutionException {
        final String artifact = buildDirectory + System.getProperty("file.separator") + buildFinalName + "." + packaging;
        getLog().info("Artefact is: " + artifact);
        try {
            int[] conflicting_identical_counts = find_class_duplicates.maine(artifact);
            int num_conflicting_implementations = conflicting_identical_counts[0];
            int num_identical_implementations = conflicting_identical_counts[1];
            its_my_party_and_ill_cry_if_i_want_to(num_conflicting_implementations, num_identical_implementations);
        }
        catch(Exception e) {
            throw new MojoExecutionException("could not find duplicates", e);
        }
    }

    private void its_my_party_and_ill_cry_if_i_want_to(int num_conflicting_implementations, int num_identical_implementations) throws MojoExecutionException {
        final boolean throw_for_conflicting_implementations = false;
        final boolean throw_for_identical_implementations = false;
        if(throw_for_conflicting_implementations && (num_conflicting_implementations > 0)) {
            throw new MojoExecutionException("found " + num_conflicting_implementations + " conflicting implementations");
        }
        if(throw_for_identical_implementations && (num_identical_implementations > 0)) {
            throw new MojoExecutionException("found " + num_identical_implementations + " identical implementations");
        }

    }

}
