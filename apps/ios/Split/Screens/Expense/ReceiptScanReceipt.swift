import PhotosUI
import SplitKit
import SwiftUI
import UIKit

/// "Bon scannen": take or pick a photo of a receipt and let the server fill in the lines.
@MainActor
struct ReceiptScanReceipt: View {
    let model: ExpenseEditorViewModel
    @State private var isShowingCamera = false
    @State private var pickedPhoto: PhotosPickerItem?

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Bon scannen")
            Text("De regels, het totaal, de datum en de omschrijving worden voor je ingevuld.")
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
            if model.isScanning {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("Bon wordt gelezen…")
                        .foregroundStyle(Theme.mutedInk)
                }
            }
            HStack(spacing: 8) {
                if CameraPicker.isAvailable {
                    Button("Maak een foto") {
                        isShowingCamera = true
                    }
                    .fullScreenCover(isPresented: $isShowingCamera) {
                        CameraPicker(
                            onImage: { image in
                                isShowingCamera = false
                                Task { await scan(image) }
                            },
                            onCancel: { isShowingCamera = false }
                        )
                        .ignoresSafeArea()
                    }
                }
                PhotosPicker(selection: $pickedPhoto, matching: .images) {
                    Text("Kies een foto")
                }
                .onChange(of: pickedPhoto) { _, item in
                    guard let item else { return }
                    pickedPhoto = nil
                    Task { await scanPicked(item) }
                }
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))
            .disabled(model.isScanning)
        }
    }

    private func scanPicked(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
            model.errorMessage = "Deze foto kon niet worden geopend. Probeer een andere foto."
            return
        }
        await scan(image)
    }

    private func scan(_ image: UIImage) async {
        await model.scanReceipt(jpegData: ReceiptImage.jpegData(from: image))
    }
}
