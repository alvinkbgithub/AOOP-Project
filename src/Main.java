import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;

/**
 * Main JavaFX Application — Unified GUI for the Inventory Management System.
 *
 * Features:
 *   - Add products via form input
 *   - View all products in a TableView
 *   - Update stock (quantity) for a selected product
 *   - Red text for products with zero quantity
 *   - Input validation
 *   - CSS-styled modern UI
 *
 * Demonstrates: JavaFX layouts (VBox, HBox, BorderPane), TableView, exception handling in UI.
 */
public class Main extends Application {

    private final ProductDAO productDAO = new ProductDAO();
    private final ObservableList<Product> productList = FXCollections.observableArrayList();
    private TableView<Product> tableView;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Inventory Management System");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        // ── Top: Title Bar ──
        Label titleLabel = new Label("Inventory Management System");
        titleLabel.getStyleClass().add("title-label");
        HBox titleBar = new HBox(titleLabel);
        titleBar.setAlignment(Pos.CENTER);
        titleBar.setPadding(new Insets(18, 0, 10, 0));
        titleBar.getStyleClass().add("title-bar");
        root.setTop(titleBar);

        // ── Center: Product Table ──
        tableView = createProductTable();
        VBox tableContainer = new VBox(10, createTableHeader(), tableView);
        tableContainer.setPadding(new Insets(0, 20, 0, 20));
        VBox.setVgrow(tableView, Priority.ALWAYS);
        root.setCenter(tableContainer);

        // ── Right: Forms Panel ──
        VBox formsPanel = createFormsPanel();
        root.setRight(formsPanel);

        // ── Bottom: Status Bar ──
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.getStyleClass().add("status-bar");
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 950, 620);

        // Load CSS stylesheet
        try {
            String css = getClass().getResource("styles.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("CSS stylesheet not found, using default styles.");
        }

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        primaryStage.show();

        // Load initial data
        refreshTable();

        // Check DB connection on startup
        if (!DBConnection.testConnection()) {
            showAlert(Alert.AlertType.WARNING, "Database Warning",
                    "Could not connect to the database.\n"
                            + "Ensure PostgreSQL is running and the 'inventory_db' database exists.");
            setStatus("Database connection failed", true);
        }
    }

    /**
     * Creates the product TableView with columns and red-text row styling.
     */
    @SuppressWarnings("unchecked")
    private TableView<Product> createProductTable() {
        TableView<Product> table = new TableView<>();

        // Proper resizing + row height fix
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(28);

        table.setPlaceholder(new Label("No products in inventory. Add one using the form."));

        // ID column
        TableColumn<Product, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId()).asObject());

        // Name column
        TableColumn<Product, String> nameCol = new TableColumn<>("Product Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        // Quantity column
        TableColumn<Product, Integer> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getQuantity()).asObject());

        // Price column
        TableColumn<Product, Double> priceCol = new TableColumn<>("Price (KES)");
        priceCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getPrice()).asObject());

        // Proper price formatting
        priceCol.setCellFactory(tc -> new TableCell<Product, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("KES %.2f", price));
                }
            }
        });

        // Info column
        TableColumn<Product, String> infoCol = new TableColumn<>("Display Info");
        infoCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDisplayInfo()));

        table.getColumns().addAll(idCol, nameCol, qtyCol, priceCol, infoCol);
        table.setItems(productList);

        // Row factory — red text for zero-quantity products
        table.setRowFactory(tv -> new TableRow<Product>() {
            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                if (empty || product == null) {
                    setStyle("");
                } else if (product.getQuantity() == 0) {
                    setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });

        return table;
    }

    /**
     * Creates the header above the table with a refresh button.
     */
    private HBox createTableHeader() {
        Label header = new Label("Product Inventory");
        header.setFont(Font.font("System", FontWeight.BOLD, 15));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> refreshTable());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox box = new HBox(10, header, spacer, refreshBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 0, 5, 0));
        return box;
    }

    /**
     * Creates the right-side forms panel with Add Product and Update Stock sections.
     */
    private VBox createFormsPanel() {
        VBox panel = new VBox(20);
        panel.setPadding(new Insets(15, 20, 15, 10));
        panel.setPrefWidth(300);
        panel.getStyleClass().add("forms-panel");

        // ── Add Product Form ──
        Label addTitle = new Label("Add New Product");
        addTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextField nameField = new TextField();
        nameField.setPromptText("Product name");

        TextField qtyField = new TextField();
        qtyField.setPromptText("Quantity");

        TextField priceField = new TextField();
        priceField.setPromptText("Price");

        Button addBtn = new Button("Add Product");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> {
            handleAddProduct(nameField, qtyField, priceField);
        });

        VBox addForm = new VBox(8, addTitle,
                new Label("Name:"), nameField,
                new Label("Quantity:"), qtyField,
                new Label("Price:"), priceField,
                addBtn);
        addForm.getStyleClass().add("form-section");
        addForm.setPadding(new Insets(12));

        // ── Update Stock Form ──
        Label updateTitle = new Label("Update Stock");
        updateTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label selectedLabel = new Label("Select a product from the table first.");
        selectedLabel.setWrapText(true);
        selectedLabel.getStyleClass().add("hint-label");

        TextField newQtyField = new TextField();
        newQtyField.setPromptText("New quantity");

        Button updateBtn = new Button("Update Quantity");
        updateBtn.getStyleClass().add("primary-button");
        updateBtn.setMaxWidth(Double.MAX_VALUE);
        updateBtn.setDisable(true);

        // Enable update when a product is selected
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedLabel.setText("Selected: " + newVal.getName() + " (ID: " + newVal.getId() + ")");
                updateBtn.setDisable(false);
            } else {
                selectedLabel.setText("Select a product from the table first.");
                updateBtn.setDisable(true);
            }
        });

        updateBtn.setOnAction(e -> {
            handleUpdateStock(newQtyField);
        });

        VBox updateForm = new VBox(8, updateTitle, selectedLabel,
                new Label("New Quantity:"), newQtyField,
                updateBtn);
        updateForm.getStyleClass().add("form-section");
        updateForm.setPadding(new Insets(12));

        // ── Delete Product ──
        Button deleteBtn = new Button("Delete Selected Product");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setDisable(true);

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteBtn.setDisable(newVal == null);
        });

        deleteBtn.setOnAction(e -> handleDeleteProduct());

        panel.getChildren().addAll(addForm, updateForm, deleteBtn);
        return panel;
    }

    /**
     * Handles adding a new product with input validation and exception handling.
     */
    private void handleAddProduct(TextField nameField, TextField qtyField, TextField priceField) {
        // Input validation
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Product name cannot be empty.");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyField.getText().trim());
            if (quantity < 0) {
                showAlert(Alert.AlertType.ERROR, "Validation Error", "Quantity must be a non-negative integer.");
                return;
            }
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Quantity must be a valid integer.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceField.getText().trim());
            if (price < 0) {
                showAlert(Alert.AlertType.ERROR, "Validation Error", "Price must be a non-negative number.");
                return;
            }
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Price must be a valid number.");
            return;
        }

        // Add to database
        try {
            Product product = new Product(name, quantity, price);
            productDAO.add(product);
            refreshTable();
            setStatus("Product '" + name + "' added successfully.", false);

            // Clear fields
            nameField.clear();
            qtyField.clear();
            priceField.clear();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    "Failed to add product:\n" + ex.getMessage());
            setStatus("Error adding product.", true);
        }
    }

    /**
     * Handles updating the stock quantity for the selected product.
     */
    private void handleUpdateStock(TextField newQtyField) {
        Product selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a product to update.");
            return;
        }

        int newQty;
        try {
            newQty = Integer.parseInt(newQtyField.getText().trim());
            if (newQty < 0) {
                showAlert(Alert.AlertType.ERROR, "Validation Error", "Quantity must be a non-negative integer.");
                return;
            }
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Please enter a valid integer for quantity.");
            return;
        }

        try {
            productDAO.updateStock(selected.getId(), newQty);
            refreshTable();
            setStatus("Stock updated for '" + selected.getName() + "' to " + newQty, false);
            newQtyField.clear();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    "Failed to update stock:\n" + ex.getMessage());
            setStatus("Error updating stock.", true);
        }
    }

    /**
     * Handles deleting the selected product.
     */
    private void handleDeleteProduct() {
        Product selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete product '" + selected.getName() + "'?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    productDAO.delete(selected.getId());
                    refreshTable();
                    setStatus("Product '" + selected.getName() + "' deleted.", false);
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Database Error",
                            "Failed to delete product:\n" + ex.getMessage());
                    setStatus("Error deleting product.", true);
                }
            }
        });
    }

    /**
     * Refreshes the table data from the database.
     */
    private void refreshTable() {
        try {
            List<Product> products = productDAO.getAll();
            productList.setAll(products);
            setStatus("Loaded " + products.size() + " product(s).", false);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    "Failed to load products:\n" + ex.getMessage());
            setStatus("Error loading products.", true);
        }
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Updates the status bar label.
     */
    private void setStatus(String text, boolean isError) {
        statusLabel.setText(text);
        if (isError) {
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        } else {
            statusLabel.setStyle("-fx-text-fill: #2ecc71;");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}