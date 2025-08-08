# 🚮 Dupes2Trash

**Dupes2Trash** is a fast, and simple desktop utility for finding and deleting duplicate files on your system. Built with Java and SWT.

---

## ✨ Features

- **Fast Scanning**: Quickly scans directories for duplicate files using efficient byte-by-byte comparison.
- **Modern UI**: Sleek, dark-mode compatible interface powered by SWT.
- **Safe Deletion**: Moves duplicates to the system trash instead of permanent deletion.
- **Cross-Platform**: Works on Windows, macOS, and Linux.
- **Side-by-Side Comparison**: View duplicate file pairs in synchronized, scroll-linked lists.

---

## 🚀 Getting Started

### Prerequisites
- Java 17 or newer
- SWT library (included in most distributions)

### Build & Run

~~~ sh
# Clone the repository
git clone https://github.com/seerainer/Dupes2Trash.git
cd Dupes2Trash

# Build with Gradle
./gradlew build

# Run the application
java -cp build/libs/Dupes2Trash-0.1.5.jar io.github.seerainer.dupes2trash.Dupes2Trash
~~~

---

## 🛠️ Usage

1. **Open Directory**: Click `File > Open Directory` and select a folder to scan.
2. **Scan**: The app will scan for duplicate files and display them in two synchronized lists.
3. **Review**: Browse the duplicates. Both lists scroll in tandem for easy comparison.
4. **Delete**: Click `File > Dupes to Trash` to safely move duplicates to your system trash.

---

## 📦 License

This project is licensed under the [Apache License 2.0](LICENSE).

---

## 🙏 Acknowledgements
- [Eclipse SWT](https://www.eclipse.org/swt/)

---

## ⭐ Star this project if you find it useful!