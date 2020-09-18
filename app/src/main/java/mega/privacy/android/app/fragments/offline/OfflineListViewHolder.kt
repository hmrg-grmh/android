package mega.privacy.android.app.fragments.offline

import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.facebook.drawee.generic.RoundingParams
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.OfflineItemListBinding
import mega.privacy.android.app.utils.Util.px2dp

class OfflineListViewHolder(
    private val binding: OfflineItemListBinding
) : OfflineViewHolder(binding.root) {
    override fun bind(position: Int, node: OfflineNode, listener: OfflineAdapterListener) {
        super.bind(position, node, listener)

        binding.threeDots.setOnClickListener { listener.onOptionsClicked(position, node) }

        if (node.selected) {
            binding.root.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, R.color.new_multiselect_color)
            )

            binding.thumbnail.setActualImageResource(R.drawable.ic_select_folder)
        } else {
            binding.root.setBackgroundColor(Color.WHITE)

            val placeHolderRes = MimeTypeList.typeForName(node.node.name).iconResourceId

            if (node.thumbnail != null) {
                binding.thumbnail.setImageURI(Uri.fromFile(node.thumbnail))
            } else {
                binding.thumbnail.setActualImageResource(if (node.node.isFolder) R.drawable.ic_folder_list else placeHolderRes)
            }
            binding.thumbnail.hierarchy.roundingParams = RoundingParams.fromCornersRadius(5F)
        }

        val res = binding.root.resources.displayMetrics
        val param = binding.thumbnail.layoutParams as FrameLayout.LayoutParams
        if (node.thumbnail == null || node.selected) {
            param.width = px2dp(LARGE_IMAGE_WIDTH, res)
            param.height = param.width
            param.marginStart = px2dp(LARGE_IMAGE_MARGIN_LEFT, res)
        } else {
            param.width = px2dp(SMALL_IMAGE_WIDTH, res)
            param.height = param.width
            param.marginStart = px2dp(SMALL_IMAGE_MARGIN_LEFT, res)
        }
        binding.thumbnail.layoutParams = param

        binding.filename.text = node.node.name
        binding.nodeInfo.text = node.nodeInfo
    }

    fun getThumbnailView(): View {
        return binding.thumbnail
    }

    companion object {
        const val LARGE_IMAGE_WIDTH = 48F
        const val LARGE_IMAGE_MARGIN_LEFT = 12F
        private const val SMALL_IMAGE_WIDTH = 36F
        private const val SMALL_IMAGE_MARGIN_LEFT = 18F
    }
}
