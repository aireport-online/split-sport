import SplitKit

/// A team on the navigation stack. Name and colour travel along so the
/// screen has a title before the team has loaded.
struct GroupRoute: Hashable {
    let id: String
    let name: String
    let color: GroupColor

    init(_ group: GroupSummary) {
        id = group.id
        name = group.name
        color = group.color
    }

    init(_ group: GroupDetail) {
        id = group.id
        name = group.name
        color = group.color
    }
}
