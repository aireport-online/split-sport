import UIKit

/// Prepares a photo of a receipt for `POST /receipts/scan`.
enum ReceiptImage {
    /// The server asks for at most 1600 px on the long side.
    private static let maxDimension: CGFloat = 1600

    /// The photo as JPEG, scaled down when it is larger than `maxDimension`.
    static func jpegData(from image: UIImage) -> Data {
        let longSide = max(image.size.width, image.size.height)
        let ratio = longSide > maxDimension ? maxDimension / longSide : 1
        let size = CGSize(
            width: max(1, (image.size.width * ratio).rounded()),
            height: max(1, (image.size.height * ratio).rounded())
        )
        // Scale 1 makes the renderer work in pixels instead of screen points.
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).jpegData(withCompressionQuality: 0.8) { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
