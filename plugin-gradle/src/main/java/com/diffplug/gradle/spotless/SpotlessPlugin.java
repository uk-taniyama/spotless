/*
 * Copyright 2016-2021 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.spotless;

import java.util.function.Consumer;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;

import com.diffplug.spotless.SpotlessCache;

public class SpotlessPlugin implements Plugin<Project> {
	static final String SPOTLESS_MODERN = "spotlessModern";
	static final String MINIMUM_GRADLE = "6.1.1";

	@Override
	public void apply(Project project) {
		if (SpotlessPluginRedirect.gradleIsTooOld(project)) {
			throw new GradleException("Spotless requires Gradle " + MINIMUM_GRADLE + " or newer, this was " + project.getGradle().getGradleVersion());
		}
		// if -PspotlessModern=true, then use the modern stuff instead of the legacy stuff
		if (project.hasProperty(SPOTLESS_MODERN)) {
			project.getLogger().warn("'spotlessModern' has no effect as of Spotless 5.0, recommend removing it.");
		}

		// setup the extension
		project.getExtensions().create(SpotlessExtension.class, SpotlessExtension.EXTENSION, SpotlessExtensionImpl.class, project);

		// clear spotless' cache when the user does a clean
		// resolution for: https://github.com/diffplug/spotless/issues/243#issuecomment-564323856
		// project.getRootProject() is consistent across every project, so only of one the clears will
		// actually happen (as desired)
		//
		// we use System.identityHashCode() to avoid a memory leak by hanging on to the reference directly
		int cacheKey = System.identityHashCode(project.getRootProject());
		configureCleanTask(project, clean -> clean.doLast(unused -> SpotlessCache.clearOnce(cacheKey)));
	}

	static void configureCleanTask(Project project, Consumer<Delete> onClean) {
		project.getTasks().withType(Delete.class).configureEach(clean -> {
			if (clean.getName().equals(BasePlugin.CLEAN_TASK_NAME)) {
				onClean.accept(clean);
			}
		});
	}

	/** clean removes the SpotlessCache, so we have to run after clean. */
	static void taskMustRunAfterClean(Project project, TaskProvider<?> task) {
		configureCleanTask(project, clean -> task.get().mustRunAfter(clean));
	}

	static String capitalize(String input) {
		return Character.toUpperCase(input.charAt(0)) + input.substring(1);
	}
}
