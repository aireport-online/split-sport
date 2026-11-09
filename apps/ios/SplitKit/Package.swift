// swift-tools-version: 5.9
import PackageDescription

// Everything of the Split iOS app that is not SwiftUI or UIKit: models, the API
// client, the split algorithm and the view models. Builds on macOS and Linux so
// it can be tested without Xcode. Tools version 5.9 keeps the Swift 5 language mode.
let package = Package(
    name: "SplitKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SplitKit", targets: ["SplitKit"]),
    ],
    targets: [
        .target(name: "SplitKit"),
        .testTarget(name: "SplitKitTests", dependencies: ["SplitKit"]),
    ]
)
