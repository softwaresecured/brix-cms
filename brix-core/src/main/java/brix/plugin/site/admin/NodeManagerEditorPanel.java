package brix.plugin.site.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.jcr.ReferentialIntegrityException;

import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import brix.Brix;
import brix.Path;
import brix.auth.Action;
import brix.auth.Action.Context;
import brix.jcr.exception.JcrException;
import brix.jcr.wrapper.BrixNode;
import brix.plugin.site.ManageNodeTabFactory;
import brix.plugin.site.SitePlugin;
import brix.plugin.site.auth.SiteNodeAction;
import brix.plugin.site.auth.SiteNodeAction.Type;
import brix.web.util.PathLabel;

public class NodeManagerEditorPanel extends Panel<BrixNode>
{

	public NodeManagerEditorPanel(String id, IModel<BrixNode> model)
	{
		super(id, model);

		Path root = new Path(SitePlugin.get().getSiteRootPath());
		add(new PathLabel("path2", new PropertyModel(this, "node.path"), root)
		{
			@Override
			protected void onPathClicked(Path path)
			{
				BrixNode node = (BrixNode) getNode().getSession().getItem(path.toString());
				selectNode(node);
			}
		});

		add(new Link("rename")
		{
			@Override
			public void onClick()
			{
				String id = NodeManagerEditorPanel.this.getId();
				Panel<BrixNode> renamePanel = new RenamePanel(id, NodeManagerEditorPanel.this.getModel())
				{
					@Override
					protected void onLeave()
					{
						replaceWith(NodeManagerEditorPanel.this);
					}
				};
				NodeManagerEditorPanel.this.replaceWith(renamePanel);
			}

			@Override
			public boolean isVisible()
			{
				Action action = new SiteNodeAction(Context.ADMINISTRATION, Type.NODE_RENAME, getNode());

				BrixNode node = NodeManagerEditorPanel.this.getModelObject();
				String path = node.getPath();
				String web = SitePlugin.get().getSiteRootPath();
				Brix brix = node.getBrix();
				return brix.getAuthorizationStrategy().isActionAuthorized(action) && path.length() > web.length()
						&& path.startsWith(web);
			}
		});

		add(new Link("makeVersionable")
		{
			@Override
			public void onClick()
			{
				if (!getNode().isNodeType("mix:versionable"))
				{
					getNode().addMixin("mix:versionable");
					getNode().save();
					getNode().checkin();
				}
			}

			@Override
			public boolean isVisible()
			{
				Action action = new SiteNodeAction(Context.ADMINISTRATION, Type.NODE_EDIT, getNode());
				return getNode() != null && getNode().isNodeType("nt:file") && !getNode().isNodeType("mix:versionable")
						&& getNode().getBrix().getAuthorizationStrategy().isActionAuthorized(action);
			}
		});

		add(new Link("delete")
		{

			@Override
			public void onClick()
			{
				BrixNode node = getNode();
				BrixNode parent = (BrixNode) node.getParent();

				selectNode(parent);

				node.remove();

				try
				{
					parent.save();
				}
				catch (JcrException e)
				{
					if (e.getCause() instanceof ReferentialIntegrityException)
					{
						parent.getSession().refresh(false);
						NodeManagerEditorPanel.this.getModel().detach();
						// parent.refresh(false);
						getSession().error("Couldn't delete node. Other nodes contain references to this node.");
						selectNode(getNode());
					}
					else
					{
						throw e;
					}
				}
			}

			@Override
			public boolean isVisible()
			{
				Action action = new SiteNodeAction(Context.ADMINISTRATION, Type.NODE_DELETE, getNode());
				Brix brix = getNode().getBrix();
				String path = getNode().getPath();
				return path.startsWith(SitePlugin.get().getSiteRootPath())
						&& path.length() > SitePlugin.get().getSiteRootPath().length()
						&& brix.getAuthorizationStrategy().isActionAuthorized(action);
			}

		});

		add(new NodeManagerTabbedPanel("tabbedPanel", getTabs(getModel())));
	}

	public BrixNode getNode()
	{
		return getModelObject();
	}

	private void selectNode(BrixNode node)
	{
		SitePlugin.get().selectNode(this, node);
	}

	private List<ITab> getTabs(IModel<BrixNode> nodeModel)
	{
		Collection<ManageNodeTabFactory> factories = nodeModel.getObject().getBrix().getConfig().getRegistry()
				.lookupCollection(ManageNodeTabFactory.POINT);
		if (factories != null && !factories.isEmpty())
		{
			int tabCount = 0;
			class Entry
			{
				ManageNodeTabFactory factory;
				List<ITab> tabs;
			}
			;
			List<Entry> list = new ArrayList<Entry>();
			for (ManageNodeTabFactory f : factories)
			{
				List<ITab> tabs = f.getManageNodeTabs(nodeModel);
				if (tabs != null && !tabs.isEmpty())
				{
					Entry e = new Entry();
					e.factory = f;
					e.tabs = tabs;
					tabCount += tabs.size();
					list.add(e);
				}
			}
			Collections.sort(list, new Comparator<Entry>()
			{
				public int compare(Entry o1, Entry o2)
				{
					return o2.factory.getPriority() - o1.factory.getPriority();
				}
			});
			List<ITab> result = new ArrayList<ITab>(tabCount);
			for (Entry e : list)
			{
				result.addAll(e.tabs);
			}
			return result;
		}
		else
		{
			return Collections.emptyList();
		}
	}
}