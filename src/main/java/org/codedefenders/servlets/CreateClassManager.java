/*
 * Copyright (C) 2016-2019 Code Defenders contributors
 *
 * This file is part of Code Defenders.
 *
 * Code Defenders is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Code Defenders is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.
 */
package org.codedefenders.servlets;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.codedefenders.beans.user.LoginBean;
import org.codedefenders.beans.message.MessagesBean;
import org.codedefenders.database.AdminDAO;
import org.codedefenders.database.DependencyDAO;
import org.codedefenders.database.GameClassDAO;
import org.codedefenders.database.KillmapDAO;
import org.codedefenders.database.MutantDAO;
import org.codedefenders.database.TestDAO;
import org.codedefenders.database.UncheckedSQLException;
import org.codedefenders.execution.BackendExecutorService;
import org.codedefenders.execution.CompileException;
import org.codedefenders.execution.Compiler;
import org.codedefenders.execution.KillMap;
import org.codedefenders.execution.LineCoverageGenerator;
import org.codedefenders.game.AssertionLibrary;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.LineCoverage;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Test;
import org.codedefenders.game.TestingFramework;
import org.codedefenders.model.Dependency;
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.util.Constants;
import org.codedefenders.util.FileUtils;
import org.codedefenders.util.JavaFileObject;
import org.codedefenders.util.ZipFileUtils;
import org.codedefenders.validation.code.CodeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.FileWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import javax.inject.Inject;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.codedefenders.servlets.admin.AdminSystemSettings.SETTING_NAME.CLASS_UPLOAD;
import static org.codedefenders.servlets.util.ServletUtils.ctx;
import static org.codedefenders.util.Constants.CUTS_DEPENDENCY_DIR;
import static org.codedefenders.util.Constants.CUTS_DIR;
import static org.codedefenders.util.Constants.CUTS_MUTANTS_DIR;
import static org.codedefenders.util.Constants.CUTS_TESTS_DIR;

/**
 * This {@link HttpServlet} handles the upload of Java class files, which includes file validation and storing.
 *
 * <p>Serves on path: {@code /class-upload}.
 *
 * @see org.codedefenders.util.Paths#CLASS_UPLOAD
 */
@WebServlet(org.codedefenders.util.Paths.CREATE_CLASS)
public class CreateClassManager extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(CreateClassManager.class);

    @Inject
    private MessagesBean messages;

    @Inject
    private LoginBean login;

    @Inject
    private BackendExecutorService backend;

    private static List<String> reservedClassNames = Arrays.asList(
            "Test.java"
    );

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final boolean classUploadEnabled = AdminDAO.getSystemSetting(CLASS_UPLOAD).getBoolValue();
        if (classUploadEnabled) {
            RequestDispatcher dispatcher = request.getRequestDispatcher(Constants.CREATE_CLASS_VIEW_JSP);
            dispatcher.forward(request, response);
        } else {
            messages.add("Class upload is disabled.");
            response.sendRedirect(ctx(request) + org.codedefenders.util.Paths.GAMES_OVERVIEW);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final boolean classUploadEnabled = AdminDAO.getSystemSetting(CLASS_UPLOAD).getBoolValue();
        if (!classUploadEnabled) {
            logger.warn("User {} tried to upload a class, but class upload is disabled.", login.getUserId());
            return;
        }

        logger.debug("Uploading CUT");

        final List<CompiledClass> compiledClasses = new LinkedList<>();

        boolean isMockingEnabled = false;
        TestingFramework testingFramework = null;
        AssertionLibrary assertionLibrary = null;
        boolean shouldPrepareAI = false;

        // Alias of the CUT
        String classAlias = null;
        // Used to check whether multiple CUTs are uploaded.
        final int cutId;
        // Used to check whether mutants have the same name as the class under test.
        final String cutFileName;
        // The directory in which the CUT is saved in.
        final Path cutDir;
        // Used to run calculate line coverage for tests
        final GameClass cut;
        // flag whether upload is with dependencies or not
        boolean withDependencies = false;
        
        String className = request.getParameter("className");
        
        String classCode = request.getParameter("fileCreateCUT");

        testingFramework = TestingFramework.valueOf(request.getParameter("testingFramework"));
        
        assertionLibrary = AssertionLibrary.valueOf(request.getParameter("assertionLibrary"));
        
       
        // Get actual parameters, because of the upload component, I can't do
        // request.getParameter before fetching the file
		
        //String classAlias = request.getParameter("classAlias");
        //File dir = new File("/Downloads/");
        //FileWriter myWriter = new FileWriter(new File(dir, className));
        //myWriter.write(classCode);
        //myWriter.close();
        //logger.warn("User {} tried to upload a class, but class upload is disabled.", className);
        //logger.warn("User {} tried to upload a class, but class upload is disabled.", classAlias);
        //logger.warn("User {} tried to upload a class, but class upload is disabled.", classCode);
        
        //return;
        // Splits request parameters by FileItem#isFormField into
        // upload and file parameters to ensure that all upload parameters
        // set before storing files.


        SimpleFile cutFile = null;
        cutFile = new SimpleFile(className);
        final List<JavaFileObject> dependencies = new ArrayList<>();
        final String fileName = className;
        logger.warn("bbb {} ", classCode);
        final String fileContent = classCode;
        logger.warn("aaa {} ", fileContent);
        cutFileName = fileName;
        if (fileContent == null) {
            logger.error("Class upload failed. Provided fileContent is null. That shouldn't happen.");
            messages.add("Class upload failed. Internal error. Sorry about that!");
            abortRequestAndCleanUp(request, response);
            return;
        }

        if (classAlias == null || classAlias.equals("")) {
            classAlias = fileName.replace(".java", "");
        }
        if (GameClassDAO.classExistsForAlias(classAlias)) {
            logger.error("Class upload failed. Given alias {} was already used.", classAlias);
            messages.add("Class upload failed. Given alias is already used.");
            abortRequestAndCleanUp(request, response);
            return;
        }

        cutDir = Paths.get(CUTS_DIR, classAlias);
        final String cutJavaFilePath;
        try {
            cutJavaFilePath = FileUtils.storeFile(cutDir, fileName, fileContent).toString();
        } catch (IOException e) {
            logger.error("Class upload failed. Could not store java file " + fileName, e);
            messages.add("Class upload failed. Internal error. Sorry about that!");
            abortRequestAndCleanUp(request, response, cutDir, compiledClasses);
            return;
        }

        final String cutClassFilePath;
        final List<JavaFileReferences> dependencyReferences = new LinkedList<>();
            try {
                cutClassFilePath = Compiler.compileJavaFileForContent(cutJavaFilePath, fileContent);
            } catch (CompileException e) {
                logger.error("Class upload failed. Could not compile {}!\n\n{}", fileName, e.getMessage());
                messages.add("Class upload failed. Could not compile " + fileName + "!\n" + e.getMessage());

                abortRequestAndCleanUp(request, response, cutDir, compiledClasses);
                return;
            } catch (IllegalStateException e) {
                logger.error("SEVERE ERROR. Could not find Java compiler. Please reconfigure your "
                        + "installed version.", e);
                messages.add("Class upload failed. Internal error. Sorry about that!");

                abortRequestAndCleanUp(request, response, cutDir, compiledClasses);
                return;
            }


        String classQualifiedName;
        try {
            classQualifiedName = FileUtils.getFullyQualifiedName(cutClassFilePath);
        } catch (IOException e) {
            logger.error("Class upload failed. Could not get fully qualified name for " + fileName, e);
            messages.add("Class upload failed. Internal error. Sorry about that!");

            abortRequestAndCleanUp(request, response, cutDir, compiledClasses);
            return;
        }

        cut = GameClass.build()
                .name(classQualifiedName)
                .alias(classAlias)
                .javaFile(cutJavaFilePath)
                .classFile(cutClassFilePath)
                .mockingEnabled(isMockingEnabled)
                .testingFramework(testingFramework)
                .assertionLibrary(assertionLibrary)
                .create();

        try {
            cutId = GameClassDAO.storeClass(cut);
        } catch (Exception e) {
            logger.warn("1 {} ", classQualifiedName);
            logger.warn("2 {} ", classAlias);
            logger.warn("3 {} ", cutJavaFilePath);
            logger.warn("4 {} ", cutClassFilePath);
            logger.warn("5 {} ", isMockingEnabled);
            logger.warn("6 {} ", testingFramework);
            logger.warn("7 {} ", assertionLibrary);
            logger.error("Class upload failed. Could not store class to database.");
            messages.add("Class upload failed. Internal error. Sorry about that!");
            abortRequestAndCleanUp(request, response, cutDir, compiledClasses);
            return;
        }

        compiledClasses.add(new CompiledClass(CompileClassType.CUT, cutId));

        if (withDependencies) {
            for (JavaFileReferences dep : dependencyReferences) {
                final int depId;
                try {
                    depId = DependencyDAO.storeDependency(new Dependency(cutId, dep.javaFile, dep.classFile));
                } catch (Exception e) {
                    logger.error("Class upload failed. Could not store dependency class to database.");
                    messages.add("Class upload failed. Internal error. Sorry about that!");
                    abortRequestAndCleanUp(request, response, cutDir, compiledClasses);
                    return;
                }

                compiledClasses.add(new CompiledClass(CompileClassType.DEPENDENCY, depId));
            }
        }

        messages.add("Class upload successful.");
        logger.info("Class upload of {} was successful", cutFileName);

        // At this point if there's test and mutants we shall run them against each other.
        // Since this is not happening in the context of a game we shall do it manually.
        List<Mutant> mutants = GameClassDAO.getMappedMutantsForClassId(cutId);
        List<Test> tests = GameClassDAO.getMappedTestsForClassId(cutId);
        try {
            // Custom Killmaps are not store in the DB for whatever reason,
            // while we need that !
            // Since gameID = -1, DAOs cannot find the class linked to this
            // game, hence its if, which is needed instead inside mutants and
            // tests
            KillMap killMap = KillMap.forCustom(tests, mutants, cutId, new ArrayList<>());
            KillmapDAO.insertManyKillMapEntries(killMap.getEntries(), cutId);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Could error while calculating killmap for successfully uploaded class.", e);
        }

        Redirect.redirectBack(request, response);
    }

    /**
     * Checks whether a given alias is valid or not. A valid alias is at least one
     * character long and has no special characters.
     *
     * @param alias the checked alias as a {@link String}
     * @return {@code true} if the alias is valid, {@code false} otherwise.
     */
    private boolean validateAlias(String alias) {
        return alias.matches("^[a-zA-Z0-9]*$");
    }

    /**
     * Adds the contents of a given zip file as mutants uploaded together with
     * a class under test.
     *
     * @param request         the request the mutants are added for.
     * @param response        the response to the request.
     * @param compiledClasses a list of previously added CUT, tests and mutants,
     *                        which need to get cleaned up once something fails.
     * @param cutId           the identifier of the class under test.
     * @param cutFileName     the file name of the class under test.
     * @param cutDir          the directory in which the class under test lies.
     * @param mutantsZipFile  the given zip file from which the mutants are added.
     * @param dependencies    dependencies required to compile the mutants.
     * @return {@code true} if addition fails, {@code fail} otherwise.
     * @throws IOException when aborting the request fails.
     */
 
    /**
     * Adds the contents of a given zip file as tests uploaded together with
     * a class under test.
     *
     * @param request         the request the tests are added for.
     * @param response        the response to the request.
     * @param compiledClasses a list of previously added CUT, tests and mutants,
     *                        which need to get cleaned up once something fails.
     * @param cutId           the identifier of the class under test.
     * @param cutDir          the directory in which the class under test lies.
     * @param cut             the class under test {@link GameClass} object.
     * @param testsZipFile    the given zip file from which the tests are added.
     * @param dependencies    dependencies required to compile the tests.
     * @return {@code true} if addition fails, {@code fail} otherwise.
     * @throws IOException when aborting the request fails.
     */

    /**
     * Aborts a given request by removing all uploaded compile classes from for
     * the database and {@code .java} and {@code .class} files from the system.
     *
     * <p>Also redirects the user.
     *
     * <p>This method should be the last thing called when aborting a request.
     *
     * @param request         The handled request.
     * @param response        The response of the handled requests.
     * @param cutDir          The directory in which all files are located, can be {@code null}.
     * @param compiledClasses A list of {@link CompiledClass}, which will get removed.
     * @param files           Optional additional files, which need to be removed.
     * @throws IOException When an error during redirecting occurs.
     */
    private static void abortRequestAndCleanUp(HttpServletRequest request,
                                               HttpServletResponse response,
                                               Path cutDir,
                                               List<CompiledClass> compiledClasses,
                                               String... files) throws IOException {
        logger.debug("Aborting request...");
        if (cutDir != null) {
            final List<Integer> cuts = new LinkedList<>();
            final List<Integer> dependencies = new LinkedList<>();
            final List<Integer> mutants = new LinkedList<>();
            final List<Integer> tests = new LinkedList<>();
            for (CompiledClass compiledClass : compiledClasses) {
                switch (compiledClass.type) {
                    case CUT:
                        cuts.add(compiledClass.id);
                        break;
                    case DEPENDENCY:
                        dependencies.add(compiledClass.id);
                        break;
                    case MUTANT:
                        mutants.add(compiledClass.id);
                        break;
                    case TEST:
                        tests.add(compiledClass.id);
                        break;
                    default:
                        // ignore
                }
            }

            try {
                logger.info("Removing directory {} again", cutDir);
                org.apache.commons.io.FileUtils.forceDelete(cutDir.toFile());
            } catch (IOException e) {
                // logged, but otherwise ignored. No need to abort while aborting.
                logger.error("Error removing directory of compiled classes.", e);
            }
            for (String file : files) {
                logger.info("Removing {} again.", file);
                try {
                    Files.delete(Paths.get(file));
                } catch (IOException ignored) {
                    // file may have been removed already.
                }

                try {
                    final Path parentFolder = Paths.get(file).getParent();
                    Files.delete(parentFolder);
                } catch (IOException ignored) {
                    // folder may have been removed already.
                }
            }

            MutantDAO.removeMutantsForIds(mutants);
            TestDAO.removeTestsForIds(tests);
            DependencyDAO.removeDependenciesForIds(dependencies);
            GameClassDAO.removeClassesForIds(cuts);
        }

        Redirect.redirectBack(request, response);
        logger.debug("Aborting request...done");
    }

    /**
     * Aborts a given request by redirecting the user.
     *
     * <p>This method should be the last thing called when aborting a request.
     *
     * @param request  The handled request.
     * @param response The response of the handled requests.
     * @throws IOException When an error during redirecting occurs.
     */
    private static void abortRequestAndCleanUp(HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        logger.debug("Aborting request without removing files...");
        Redirect.redirectBack(request, response);
        logger.debug("Aborting request without removing files...done");
    }

    /**
     * Container for a file with its name and content.
     *
     * <p>Name is stored as a {@link String}, content as a {@code byte[]}.
     */
    private static class SimpleFile {
        private String fileName;
        private byte[] fileContent;

        SimpleFile(String fileName, byte[] fileContent) {
            this.fileName = fileName;
            this.fileContent = fileContent;
        }

		public SimpleFile(String className) {
			// TODO Auto-generated constructor stub
		}
    }

    /**
     * Container for paths to {@code .java} and {@code .class}
     * files of a java class.
     */
    private static class JavaFileReferences {
        private String javaFile;
        private String classFile;

        JavaFileReferences(String javaFile, String classFile) {
            this.javaFile = javaFile;
            this.classFile = classFile;
        }
    }

    /**
     * Wrapper class for classes, which have been compiled already.
     * They have a type {@link CompileClassType}, an {@code id} and
     * paths to {@code .java} and {@code .class} files.
     */
    private static class CompiledClass {
        private CompileClassType type;
        private Integer id;

        CompiledClass(CompileClassType type, Integer id) {
            this.type = type;
            this.id = id;
        }
    }

    private enum CompileClassType {
        CUT,
        DEPENDENCY,
        MUTANT,
        TEST
    }

    private ServletFileUpload servletFileUpload;

    // Enable minimal testing
    @Deprecated
    void setServletFileUpload(ServletFileUpload servletFileUpload) {
        this.servletFileUpload = servletFileUpload;
    }
}
