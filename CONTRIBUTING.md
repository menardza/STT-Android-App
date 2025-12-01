# Contributing to Android STT App

Thank you for your interest in contributing to Android STT App! This document provides guidelines and instructions for contributing.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/androidSTTapp.git
   cd androidSTTapp
   ```
3. **Set up the development environment**:
   - Install Android Studio Hedgehog or later
   - Ensure Android SDK 34 is installed
   - Open the project in Android Studio and let it sync

## Development Guidelines

### Code Style

- Follow Kotlin official code style (configured in `gradle.properties`)
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and single-purpose
- Follow existing code patterns in the project

### Commit Messages

- Use clear, descriptive commit messages
- Start with a verb in imperative mood (e.g., "Add", "Fix", "Update")
- Reference issue numbers if applicable (e.g., "Fix #123: ...")

Example:
```
Add verbose comments to VoiceActivityDetector

- Added comprehensive documentation to all methods
- Explained RMS calculation and audio processing
- Documented detection loop and state management
```

### Pull Request Process

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Write clean, well-commented code
   - Test your changes thoroughly
   - Update documentation if needed

3. **Test your changes**:
   - Build the project: `./gradlew assembleDebug`
   - Test on a physical device or emulator
   - Verify all features still work correctly

4. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Your descriptive commit message"
   ```

5. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create a Pull Request**:
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your fork and branch
   - Fill out the PR template with:
     - Description of changes
     - Testing performed
     - Screenshots (if UI changes)

## Areas for Contribution

### High Priority

- **Wake Word Detection**: Replace placeholder implementation with real ML-based detection (TensorFlow Lite, ONNX Runtime, etc.)
- **Error Handling**: Improve error messages and recovery
- **Testing**: Add unit tests and instrumentation tests
- **Performance**: Optimize audio processing and memory usage

### Medium Priority

- **UI Improvements**: Enhance the user interface
- **Accessibility**: Improve accessibility features
- **Documentation**: Expand code comments and documentation
- **Localization**: Add support for multiple languages

### Low Priority

- **Features**: New features and enhancements
- **Code Refactoring**: Improve code structure and organization
- **Build System**: Improve build scripts and configuration

## Reporting Issues

When reporting issues, please include:

1. **Description**: Clear description of the issue
2. **Steps to Reproduce**: Detailed steps to reproduce the issue
3. **Expected Behavior**: What should happen
4. **Actual Behavior**: What actually happens
5. **Environment**:
   - Android version
   - Device model
   - App version
   - Logcat output (if applicable)

## Code Review

All contributions go through code review. Please:

- Be responsive to feedback
- Make requested changes promptly
- Ask questions if something is unclear
- Be respectful and constructive in discussions

## Questions?

If you have questions about contributing, feel free to:

- Open an issue with the "question" label
- Check existing issues and discussions
- Review the code and documentation

Thank you for contributing to Android STT App! 🎉

