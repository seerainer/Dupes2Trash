/*
 * Copyright 2025 Philipp Seerainer
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
 *
 */
package io.github.seerainer.dupes2trash;

import static org.eclipse.swt.events.MenuListener.menuShownAdapter;
import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Stack;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

/**
 * Dupes2Trash
 *
 * Moves duplicate files to the trash.
 */
public class Dupes2Trash {

	private static final int BUFFER_SIZE = 8192;
	private static final boolean DARK_MODE = Display.isSystemDarkTheme();
	private static final Color DARK_BACK = new Color(50, 50, 50);
	private static final Color DARK_FORE = new Color(220, 220, 220);
	private static final Display DISPLAY = Display.getDefault();
	private List listA;
	private List listB;
	private Shell shell;
	private String dir;

	private Dupes2Trash(final String[] args) {
		if (args.length > 0) {
			this.dir = args[0];
		}
	}

	private static boolean contentEquals(final String path1, final String path2) {
		try (var fis1 = new FileInputStream(path1); var fis2 = new FileInputStream(path2)) {
			final var buffer1 = new byte[BUFFER_SIZE];
			final var buffer2 = new byte[BUFFER_SIZE];

			while (true) {
				final var bytesRead1 = fis1.read(buffer1);
				final var bytesRead2 = fis2.read(buffer2);

				if (bytesRead1 != bytesRead2) {
					return false;
				}
				if (bytesRead1 == -1) {
					break;
				}
				if (!Arrays.equals(Arrays.copyOf(buffer1, bytesRead1), Arrays.copyOf(buffer2, bytesRead2))) {
					return false;
				}
			}
			return true;
		} catch (final IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static void main(final String[] args) {
		System.setProperty("org.eclipse.swt.display.useSystemTheme", "true");
		try {
			final var shell = new Dupes2Trash(args).open(DISPLAY);

			while (!shell.isDisposed()) {
				if (!DISPLAY.readAndDispatch()) {
					DISPLAY.sleep();
				}
			}
		} finally {
			DARK_BACK.dispose();
			DARK_FORE.dispose();
			DISPLAY.dispose();
		}
	}

	private static MenuItem menuItem(final Menu parent, final int state, final Menu menu, final SelectionListener listener,
			final int acc, final String text) {
		final var item = new MenuItem(parent, state);

		if (menu != null) {
			item.setMenu(menu);
		}
		if (listener != null) {
			item.addSelectionListener(listener);
		}
		if (acc > 0) {
			item.setAccelerator(acc);
		}
		if (text != null) {
			item.setText(text);
		}

		return item;
	}

	private static void setColors(final Control control) {
		if (!DARK_MODE) {
			return;
		}
		control.setBackground(DARK_BACK);
		control.setForeground(DARK_FORE);
	}

	private void compare() {
		final var progressShell = new Shell(shell, SWT.APPLICATION_MODAL | SWT.ON_TOP);
		progressShell.setText("Comparing Files...");
		progressShell.setLayout(new FillLayout(SWT.VERTICAL));
		final var label = new Label(progressShell, SWT.NONE);
		label.setText("Comparing files, please wait...");
		setColors(label);
		final var progressBar = new ProgressBar(progressShell, SWT.HORIZONTAL);
		progressBar.setMinimum(0);

		final var files = search();
		progressBar.setMaximum(files.size());
		progressShell.setSize(300, 100);

		final var display = shell.getDisplay();
		final var primary = display.getPrimaryMonitor();
		final var bounds = primary.getBounds();
		final var shellBounds = progressShell.getBounds();
		final var x = bounds.x + (bounds.width - shellBounds.width) / 2;
		final var y = bounds.y + (bounds.height - shellBounds.height) / 2;
		progressShell.setLocation(x, y);

		progressShell.open();

		new Thread(() -> {
			final var addedFiles = new HashSet<String>();
			for (var i = 0; i < files.size(); i++) {
				final var f1 = files.get(i);
				if (f1.isFile()) {
					for (var j = i + 1; j < files.size(); j++) {
						final var f2 = files.get(j);
						if (f2.isFile() && f1.length() == f2.length()) {
							final var s1 = f1.getAbsolutePath();
							final var s2 = f2.getAbsolutePath();
							if (!addedFiles.contains(s2) && contentEquals(s1, s2)) {
								shell.getDisplay().asyncExec(() -> {
									listA.add(s1);
									listB.add(s2);
								});
								addedFiles.add(s2);
							}
						}
					}
				}
				final var progress = i + 1;
				shell.getDisplay().asyncExec(() -> progressBar.setSelection(progress));
			}
			shell.getDisplay().asyncExec(() -> {
				progressShell.close();
				listA.setRedraw(true);
				listB.setRedraw(true);

				if (listA.getItemCount() > 0) {
					listA.setEnabled(true);
					listB.setEnabled(true);

					final var mb = message(SWT.OK | SWT.CANCEL | SWT.ICON_WARNING,
							listA.getItemCount() + " duplicate file(s) found!\n\nMove to trash?", "Warning");

					if (mb == SWT.OK) {
						moveToTrash();
					}
				} else {
					listA.setEnabled(false);
					listB.setEnabled(false);
					message(SWT.OK | SWT.ICON_INFORMATION, "0 duplicate files found!", "Info");
				}
			});
		}).start();
	}

	private int message(final int style, final String message, final String text) {
		final var mb = new MessageBox(shell, style);
		mb.setMessage(message);
		mb.setText(text);
		return mb.open();
	}

	private void moveToTrash() {
		final var desktop = Desktop.getDesktop();
		var i = 0;

		while (listA.getItemCount() > 0) {
			final var f = new File(listB.getItem(0));

			if (f.exists() && f.canRead() && f.canWrite() && f.isFile() && desktop.moveToTrash(f)) {
				i++;
			}

			listA.remove(0);
			listB.remove(0);
		}

		message(SWT.OK | SWT.ICON_INFORMATION, i + " file(s) moved to trash!", "Info");
		listA.setEnabled(false);
		listB.setEnabled(false);
	}

	private Shell open(final Display display) {
		if ("win32".equals(SWT.getPlatform()) && DARK_MODE) {
			display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", Boolean.TRUE);
			display.setData("org.eclipse.swt.internal.win32.useShellTitleColoring", Boolean.TRUE);
			display.setData("org.eclipse.swt.internal.win32.all.use_WS_BORDER", Boolean.TRUE);
			display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", DARK_FORE);
			display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", DARK_BACK);
		}

		shell = new Shell(display, SWT.SHELL_TRIM);
		shell.setMenuBar(new Menu(shell, SWT.BAR));
		shell.setLayout(new FillLayout());
		shell.setText("Dupes2Trash | Delete Duplicate Files");

		final var file = new Menu(shell, SWT.DROP_DOWN);
		menuItem(shell.getMenuBar(), SWT.CASCADE, file, null, 0, "&File");
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(_ -> openDir()), 0, "&Open Directory");
		final var delete = menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(_ -> moveToTrash()), 0, "&Dupes to Trash");
		menuItem(file, SWT.SEPARATOR, null, null, 0, null);
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(_ -> shell.close()), SWT.ESC, "E&xit\tEsc");
		file.addMenuListener(menuShownAdapter(_ -> delete.setEnabled(listA.getItemCount() > 0)));

		final var form = new SashForm(shell, SWT.HORIZONTAL);
		form.setLayout(new FillLayout());
		form.setSashWidth(1);

		listA = new List(form, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		listB = new List(form, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		listA.setEnabled(false);
		listB.setEnabled(false);
		setColors(listA);
		setColors(listB);

		syncListScrolling();

		form.setWeights(50, 50);

		shell.open();

		if (dir != null) {
			openDir();
		}

		return shell;
	}

	private void openDir() {
		if (dir == null) {
			dir = new DirectoryDialog(shell).open();
		}
		if (dir == null) {
			return;
		}

		final var f = new File(dir);

		if (f.exists() && f.isDirectory()) {
			compare();
		}

		dir = null;
	}

	private java.util.List<File> search() {
		final java.util.List<File> files = new ArrayList<>();
		final var dirs = new Stack<File>();
		final var startdir = new File(dir);

		if (startdir.isDirectory()) {
			dirs.push(startdir);
		}

		while (dirs.size() > 0) {
			for (final var file : dirs.pop().listFiles()) {
				if (file.isDirectory()) {
					dirs.push(file);
				} else if (file.isFile()) {
					files.add(file);
				}
			}
		}

		return files;
	}

	private void syncListScrolling() {
		listA.addListener(SWT.MouseVerticalWheel, _ -> listB.setTopIndex(listA.getTopIndex()));
		listB.addListener(SWT.MouseVerticalWheel, _ -> listA.setTopIndex(listB.getTopIndex()));
		listA.addListener(SWT.Selection, _ -> listB.setTopIndex(listA.getTopIndex()));
		listB.addListener(SWT.Selection, _ -> listA.setTopIndex(listB.getTopIndex()));
	}
}